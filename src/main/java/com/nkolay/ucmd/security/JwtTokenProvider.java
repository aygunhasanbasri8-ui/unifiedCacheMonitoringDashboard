package com.nkolay.ucmd.security;

import com.nkolay.ucmd.model.entity.User;

import java.util.Base64;



public class JwtTokenProvider {
    private static final String SECRET_KEY;
    private static final long EXPIRATION_TIME;

    static {
        SECRET_KEY = getEnvVariable("JWT_SECRET", null);
        if (SECRET_KEY == null || SECRET_KEY.isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is not set. " +
                            "Set it via .env file or system environment variables."
            );
        }
        if (SECRET_KEY.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters long for security."
            );
        }

        String expirationHours = getEnvVariable("JWT_EXPIRATION_HOURS", "24");
        EXPIRATION_TIME = Long.parseLong(expirationHours) * 60 * 60 * 1000;
    }

    private static String getEnvVariable(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }


    /**
     *Demo için JWT
     *
     **/
    public static String generateToken(User user) {
        long now = System.currentTimeMillis();
        long expiryDate = now + EXPIRATION_TIME;

        //Basic format: username:role:expiryTime
        String payload = user.getUsername() + ":" + user.getRole() + ":" + expiryDate;
        String encoded = Base64.getEncoder().encodeToString(payload.getBytes());

        //Siganture
        String signature = Base64.getEncoder().encodeToString(
                (payload + SECRET_KEY).getBytes()
        ).substring(0,20);

        return encoded + "." + signature;
    }

    /**
     *Token Validate
     *
     */
    public static boolean validateToken(String token) {
        try {
            if (token == null || !token.contains(".")){
                return false;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 2) return false;

            String payload = new String(Base64.getDecoder().decode(parts[0]));
            String[] data = payload .split(":");

            if (data.length < 3) return false;

            long expiryDate = Long.parseLong(data[2]);
            return System.currentTimeMillis() < expiryDate;
        }catch (Exception e) {
            return false;
        }
    }


    /**
     * Token-->username
     */
    public static String getUsernameFromToken(String token){
        try{
            String payload = new String(Base64.getDecoder().decode(token.split("\\.")[0]));
            return payload.split(":")[0];
        } catch (Exception e) {
            return null;
        }
    }


    /**
     * Token-->role
     *
     */
    public static String getRoleFromToken(String token) {
        try {
            String payload = new String(Base64.getDecoder().decode(token.split("\\.")[0]));
            return payload.split(":")[1];
        } catch (Exception e) {
            return null;
        }
    }

    public static long getExpiryFromToken(String token) {
        String payload = new String(Base64.getDecoder().decode(token.split("\\.")[0]));
        return Long.parseLong(payload.split(":")[2]);   // index 2 = expiry
    }

}
