package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.User;
import com.nkolay.ucmd.model.LoginResponse;
import com.nkolay.ucmd.security.SimplePasswordEncoder;
import com.nkolay.ucmd.security.JwtTokenProvider;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {

    private Map<String,User> users = new HashMap<>();

    public UserService() {
        // Dev ortamında test kullanıcılarını şifreleri .env'den oku
        boolean isProduction = "production".equalsIgnoreCase(System.getenv("APP_ENVIRONMENT"));

        if (!isProduction) {
            // Dev ortamında test user'ları oluştur
            String monitorPass = System.getenv("TEST_USER_MONITOR_PASSWORD");
            String adminPass = System.getenv("TEST_USER_ADMIN_PASSWORD");


            if (monitorPass == null || adminPass == null) {
                throw new IllegalStateException(
                        "Test user passwords not configured. Set TEST_USER_MONITOR_PASSWORD " +
                                "and TEST_USER_ADMIN_PASSWORD in .env"
                );
            }

            User monitor = new User(
                    "monitor",
                    SimplePasswordEncoder.encode(monitorPass),
                    "monitor@ucmd.local",
                    "MONITOR"
            );
            monitor.setId(1L);
            users.put("monitor", monitor);

            User admin = new User(
                    "admin",
                    SimplePasswordEncoder.encode(adminPass),
                    "admin@ucmd.local",
                    "ADMIN"
            );
            admin.setId(2L);
            users.put("admin", admin);
        }
        // Prod'da kullanıcılar veritabanından yüklenecek (application-prod.yml ile)
    }

    /**
     * USER AUTHENTICATION
     *
     */
    public LoginResponse authenticate(String username, String password){
        User user =users.get(username);

        if ( user==null || !user.isActive()) {
            return null;
        }

        if (!SimplePasswordEncoder.matches(password, user.getPasswordHash())) {
            return null;

        }

        String token= JwtTokenProvider.generateToken(user);
        user.setLastLoginAt((System.currentTimeMillis()));

        return new LoginResponse(token, user.getUsername(), user.getRole(), 24*60*60);

    }

    /**
     * USER FROM TOKEN
     *
     */
    public User getUserFromToken(String token) {
        String username = JwtTokenProvider.getUsernameFromToken(token);
        return username != null ? users.get(username): null;
    }

    /**
     * NEW USER
     *
     */
    public User createUser(String username, String password, String email, String role) {
        User user = new User(
                username,
                SimplePasswordEncoder.encode(password),
                email,
                role
        );
        user.setId((long)(users.size() + 1));
        users.put(username,user);
        return user;
    }

    /**
     * GET USER
     */
    public java.util.Collection<User> getAllUsers() {
        return users.values();
    }
}
