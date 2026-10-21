package com.ucmd.security;

import com.ucmd.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Denetim kaydı için istekten kullanıcı ve IP çözer.
 * AuthInterceptor isteğe "currentUser" attribute'unu koyar.
 */
@Component
public class AuditContext {

    private static final String ANONYMOUS = "ANONYMOUS";

    /** AuthInterceptor'ın koyduğu currentUser'dan kullanıcı adı. */
    public String currentUsername(HttpServletRequest request) {
        if (request == null) return ANONYMOUS;
        Object user = request.getAttribute("currentUser");
        if (user instanceof User u) return u.getUsername();
        return ANONYMOUS;
    }

    /** X-Forwarded-For varsa ilk değeri, yoksa remote addr. */
    public String clientIp(HttpServletRequest request) {
        if (request == null) return null;

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0) ? forwarded.substring(0, comma) : forwarded;
            first = first.trim();
            if (!first.isEmpty()) return first;
        }
        return request.getRemoteAddr();
    }
}
