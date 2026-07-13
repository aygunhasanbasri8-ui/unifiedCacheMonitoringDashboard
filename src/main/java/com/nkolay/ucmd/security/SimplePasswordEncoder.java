package com.nkolay.ucmd.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class SimplePasswordEncoder {

    /**
    *Demo sürüm SHA256
    * Deployda Bcrypt
    **/
    public static String encode(String password) {
        try{
            MessageDigest digest =MessageDigest.getInstance("SHA-256");
            byte[] hash=digest.digest(password.getBytes((StandardCharsets.UTF_8)));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * Şifre Kontrol
     **/
    public static boolean matches(String rawPassword, String encodePassword){
        return encode(rawPassword).equals(encodePassword);
    }

}
