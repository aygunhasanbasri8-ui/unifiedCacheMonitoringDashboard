package com.ucmd.security;

import com.ucmd.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtTokenProvider {

    private static final SecretKey SECRET_KEY;
    private static final long EXPIRATION_TIME;

    static {
        String secret = getEnvVariable("JWT_SECRET", null);
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. " +
                            "Set it via .env file or system environment variables.");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters long for security.");
        }
        SECRET_KEY = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String expirationHours = getEnvVariable("JWT_EXPIRATION_HOURS", "24");
        EXPIRATION_TIME = Long.parseLong(expirationHours) * 60 * 60 * 1000;
    }

    private static String getEnvVariable(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }


    public static String generateToken(User user) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole())
                .issuedAt(new Date(now))
                .expiration(new Date(now + EXPIRATION_TIME))
                .signWith(SECRET_KEY)
                .compact();
    }


    public static boolean validateToken(String token) {
        try {
            parseClaims(token);   // imza ve süre kontrolü
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public static String getUsernameFromToken(String token) {
        try {
            return parseClaims(token).getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getRoleFromToken(String token) {
        try {
            return parseClaims(token).get("role", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static long getExpiryFromToken(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            return exp != null ? exp.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}