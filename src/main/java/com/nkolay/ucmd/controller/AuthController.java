package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.LoginRequest;
import com.nkolay.ucmd.model.dto.LoginResponse;
import com.nkolay.ucmd.security.AuditContext;
import com.nkolay.ucmd.security.JwtTokenProvider;
import com.nkolay.ucmd.security.TokenBlacklist;
import com.nkolay.ucmd.service.UserService;
import com.nkolay.ucmd.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "Kimlik doğrulama (JWT)")
@RestController
@RequestMapping("/v1/auth")

public class AuthController {

    
    private final UserService userService;

    
    private final TokenBlacklist tokenBlacklist;

    
    private final AuditService auditService;

    
    private final AuditContext auditContext;

    public AuthController(UserService userService, TokenBlacklist tokenBlacklist, AuditService auditService, AuditContext auditContext) {
        this.userService = userService;
        this.tokenBlacklist = tokenBlacklist;
        this.auditService = auditService;
        this.auditContext = auditContext;
    }

    @Operation(summary = "Giriş",
               description = "Kullanıcı adı/parola karşılığında JWT üretir. "
                       + "Başarı ve başarısızlık denetim kaydına yazılır.")
    @ApiResponse(responseCode = "200", description = "Giriş başarılı, token döner")
    @ApiResponse(responseCode = "401", description = "Kullanıcı adı veya parola hatalı")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        String username = (request != null) ? request.getUsername() : null;
        String ip = auditContext.clientIp(httpRequest);
        try{
            LoginResponse response = userService.authenticate(username, request.getPassword());

            if (response != null) {
                auditService.log("LOGIN_SUCCESS", username, "User logged in", "SUCCESS", ip);
                return ResponseEntity.ok(response);
            }

            auditService.log("LOGIN_FAILED", username, "Invalid credentials", "DENIED", ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorDetail("Unauthorized", "Invalid username or password", 401));

        } catch (Exception e) {
            auditService.log("LOGIN_ERROR", username, e.getMessage(), "ERROR", ip);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorDetail("Internal Server Error", e.getMessage(), 500));

        }


    }


    @Operation(summary = "Çıkış",
               description = "Token'ı kara listeye alır; süresi dolana kadar reddedilir.")
    @ApiResponse(responseCode = "200", description = "Çıkış başarılı")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader,
                                    HttpServletRequest httpRequest) {
        String username = "ANONYMOUS";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String resolved = JwtTokenProvider.getUsernameFromToken(token);
            if (resolved != null) username = resolved;
            long expiry = JwtTokenProvider.getExpiryFromToken(token);
            tokenBlacklist.blacklist(token, expiry);
        }
        auditService.log("LOGOUT", username, "User logged out", "SUCCESS",
                auditContext.clientIp(httpRequest));
        return ResponseEntity.ok().body("Logged out successfully");
    }

    //Inner class
    public static class ErrorDetail {
        public String error;
        public String message;
        public int status;

        public ErrorDetail(String error, String message, int status) {
            this.error = error;
            this.message = message;
            this.status = status;
        }

        public String getError() {return error; }
        public String getMessage() {return message; }
        public int getStatus() {return status; }
    }
}
