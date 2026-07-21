package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.entity.User;
import com.nkolay.ucmd.model.dto.LoginResponse;
import com.nkolay.ucmd.repository.UserRepository;
import com.nkolay.ucmd.security.JwtTokenProvider;
import com.nkolay.ucmd.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void initDefaultUsers() {
        boolean isProduction = "production".equalsIgnoreCase(System.getenv("APP_ENVIRONMENT"));
        if (isProduction) {
            return;
        }

        String monitorPass = System.getenv("TEST_USER_MONITOR_PASSWORD");
        String adminPass = System.getenv("TEST_USER_ADMIN_PASSWORD");

        if (monitorPass == null || adminPass == null) {
            throw new IllegalStateException(
                    "Test user passwords not configured. Set TEST_USER_MONITOR_PASSWORD " +
                            "and TEST_USER_ADMIN_PASSWORD in .env");
        }

        // DB boşsa varsayılan kullanıcıları ekle (BCrypt ile)
        if (!userRepository.existsByUsername("monitor")) {
            User monitor = new User("monitor",
                    passwordEncoder.encode(monitorPass),
                    "monitor@ucmd.local", "MONITOR");
            userRepository.save(monitor);
        }
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User("admin",
                    passwordEncoder.encode(adminPass),
                    "admin@ucmd.local", "ADMIN");
            userRepository.save(admin);
        }
    }

    @Override
    public LoginResponse authenticate(String username, String password) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isActive()) {
            return null;
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }

        String token = JwtTokenProvider.generateToken(user);
        user.setLastLoginAt(System.currentTimeMillis());
        userRepository.save(user);

        return new LoginResponse(token, user.getUsername(), user.getRole(), 24 * 60 * 60);
    }

    @Override
    public User getUserFromToken(String token) {
        String username = JwtTokenProvider.getUsernameFromToken(token);
        if (username == null) return null;
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    public User createUser(String username, String password, String email, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Kullanıcı adı zaten mevcut: " + username);
        }
        User user = new User(username,
                passwordEncoder.encode(password),
                email, role);
        return userRepository.save(user);
    }

    @Override
    public Collection<User> getAllUsers() {
        return userRepository.findAll();
    }


    @Override
    public void deleteUser(String username) {
        // Varsayılan monitor kullanıcısı silinemez
        if ("monitor".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException("Varsayılan 'monitor' kullanıcısı silinemez");
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            throw new IllegalArgumentException("Kullanıcı bulunamadı: " + username);
        }
        userRepository.delete(user);
    }
}