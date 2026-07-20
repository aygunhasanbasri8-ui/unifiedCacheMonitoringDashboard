package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.LoginRequest;
import com.nkolay.ucmd.model.dto.LoginResponse;
import com.nkolay.ucmd.security.JwtTokenProvider;
import com.nkolay.ucmd.security.TokenBlacklist;
import com.nkolay.ucmd.service.UserService;
import com.nkolay.ucmd.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")

public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private TokenBlacklist tokenBlacklist;

    @Autowired
    private AuditService auditService;
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try{
            LoginResponse response = userService.authenticate(request.getUsername(), request.getPassword());

            if (response != null) {
                auditService.log("LOGIN_SUCCESS", request.getUsername(), "User logged in", "SUCCESS");
                return ResponseEntity.ok(response);
            }

            auditService.log("LOGIN_FAILED", request.getUsername(), "Invalid credentials", "DENIED");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorDetail("Unauthorized", "Invalid username or password", 401));

        } catch (Exception e) {
            auditService.log("LOGIN_ERROR", "SYSTEM", e.getMessage(), "ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorDetail("Internal Server Error", e.getMessage(), 500));

        }


    }


    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            long expiry = JwtTokenProvider.getExpiryFromToken(token);
            tokenBlacklist.blacklist(token, expiry);
        }
        auditService.log("LOGOUT", "SYSTEM", "User logged out", "SUCCESS");
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
