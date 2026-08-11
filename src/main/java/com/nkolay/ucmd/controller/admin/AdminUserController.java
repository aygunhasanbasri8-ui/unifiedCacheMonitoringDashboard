package com.nkolay.ucmd.controller.admin;

import com.nkolay.ucmd.model.entity.User;
import com.nkolay.ucmd.security.AuditContext;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.AuditService;
import com.nkolay.ucmd.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Admin — Users", description = "Kullanıcı yönetimi (yalnızca ADMIN)")
@RestController
@RequestMapping("/v1/admin/users")
@RequireRole("ADMIN")
public class AdminUserController {

    
    private final UserService userService;

    
    private final AuditService auditService;

    
    private final AuditContext auditContext;

    public AdminUserController(UserService userService, AuditService auditService, AuditContext auditContext) {
        this.userService = userService;
        this.auditService = auditService;
        this.auditContext = auditContext;
    }

    @Operation(summary = "Kullanıcı listesi",
               description = "Kayıtlı kullanıcılar. Parola özeti dönmez.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
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

    @Operation(summary = "Kullanıcı oluştur",
               description = "Parola BCrypt ile özetlenir. Rol verilmezse MONITOR atanır.")
    @ApiResponse(responseCode = "200", description = "Oluşturuldu")
    @ApiResponse(responseCode = "400", description = "Kullanıcı adı/parola eksik veya kullanıcı zaten var")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request,
                                        HttpServletRequest httpRequest) {
        if (request.username == null || request.username.isEmpty()
                || request.password == null || request.password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Kullanıcı adı ve şifre zorunlu"));
        }
        String role = (request.role != null && !request.role.isEmpty()) ? request.role : "MONITOR";
        User created = userService.createUser(
                request.username, request.password, role);

        auditService.log("USER_CREATED",
                auditContext.currentUsername(httpRequest),
                "username=" + created.getUsername() + ", role=" + created.getRole(),
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(Map.of(
                "id", created.getId(),
                "username", created.getUsername(),
                "role", created.getRole()));
    }

    @Operation(summary = "Kullanıcı sil",
               description = "Varsayılan 'monitor' ve 'admin' kullanıcıları silinemez.")
    @ApiResponse(responseCode = "200", description = "Silindi")
    @ApiResponse(responseCode = "400", description = "Kullanıcı bulunamadı veya silinemez")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    @DeleteMapping("/{username}")
    public ResponseEntity<?> deleteUser(@PathVariable String username,
                                        HttpServletRequest httpRequest) {
        userService.deleteUser(username);

        auditService.log("USER_DELETED",
                auditContext.currentUsername(httpRequest),
                "username=" + username,
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(Map.of("message", "Kullanıcı silindi: " + username));
    }

    public static class CreateUserRequest {
        public String username;
        public String password;
        public String role;
    }
}
