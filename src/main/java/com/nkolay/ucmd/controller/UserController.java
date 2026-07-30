package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.User;
import com.nkolay.ucmd.service.UserService;
import com.nkolay.ucmd.security.RequireRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/users")
@RequireRole("ADMIN")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> users = userService.getAllUsers().stream()
                .map(u -> {
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("id", u.getId());
                    m.put("username", u.getUsername());
                    m.put("role", u.getRole());
                    m.put("active", u.isActive());
                    m.put("createdAt", u.getCreatedAt());
                    m.put("lastLoginAt", u.getLastLoginAt());
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (request.username == null || request.username.isEmpty()
                || request.password == null || request.password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Kullanıcı adı ve şifre zorunlu"));
        }
        String role = (request.role != null && !request.role.isEmpty()) ? request.role : "MONITOR";
        User created = userService.createUser(
                request.username, request.password, role);
        return ResponseEntity.ok(Map.of(
                "id", created.getId(),
                "username", created.getUsername(),
                "role", created.getRole()));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<?> deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return ResponseEntity.ok(Map.of("message", "Kullanıcı silindi: " + username));
    }

    public static class CreateUserRequest {
        public String username;
        public String password;
        public String role;
    }
}