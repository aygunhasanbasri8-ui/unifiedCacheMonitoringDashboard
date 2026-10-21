package com.ucmd.security;

import com.ucmd.model.entity.User;
import com.ucmd.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final UserService userService;

    private final TokenBlacklist tokenBlacklist;

    public AuthInterceptor(UserService userService, TokenBlacklist tokenBlacklist) {
        this.userService = userService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // CORS preflight (OPTIONS) istekleri auth'tan muaf
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();


        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing or invalid token\"}");
            return false;
        }

        String token = authHeader.substring(7);

        if (!JwtTokenProvider.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired token\"}");
            return false;
        }

        if (tokenBlacklist.isBlacklisted(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Token has been invalidated (logged out)\"}");
            return false;
        }


        User user = userService.getUserFromToken(token);
        if (user == null || !user.isActive()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"User not found\"}");
            return false;
        }

        request.setAttribute("currentUser", user);

        // Savunma derinliği: @RequireRole unutulsa bile /v1/admin/** korunur
        if (path.contains("/v1/admin/") && !"ADMIN".equalsIgnoreCase(user.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Admin yetkisi gerekli\"}");
            return false;
        }

        if (handler instanceof org.springframework.web.method.HandlerMethod handlerMethod) {
            RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (requireRole == null) {
                requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requireRole != null) {
                String requiredRole = requireRole.value();
                String userRole = user.getRole();
                boolean allowed = requiredRole.equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole);
                if (!allowed) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"error\": \"Bu işlem için '" + requiredRole + "' yetkisi gerekli\"}");
                    return false;
                }
            }
        }

        return true;
    }
}