package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.AuditLog;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Audit", description = "Denetim kaydı sorgulama (yalnızca ADMIN)")
@RestController
@RequestMapping("/v1/audit")
@RequireRole("ADMIN")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Operation(summary = "Denetim kayıtları",
               description = "Sayfalı denetim kaydı. action veya username ile filtrelenebilir. "
                       + "Saklama süresi 90 gündür. Metrik uçları loglanmaz.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "403", description = "ADMIN yetkisi gerekli")
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        int safeSize = Math.min(Math.max(size, 1), 200);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);

        if (action != null && !action.isEmpty()) {
            return ResponseEntity.ok(auditService.getAuditLogsByAction(action, pageable));
        }
        if (username != null && !username.isEmpty()) {
            return ResponseEntity.ok(auditService.getAuditLogsByUsername(username, pageable));
        }
        return ResponseEntity.ok(auditService.getAuditLogs(pageable));
    }
}
