package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.AuditLog;
import com.nkolay.ucmd.service.AuditService;
import com.nkolay.ucmd.security.RequireRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/audit")
@RequireRole("ADMIN")
public class AuditController {

    @Autowired
    private AuditService auditService;

    @GetMapping
    public ResponseEntity<List<AuditLog>> getAuditLogs(
            @RequestParam(required = false) String action) {
        List<AuditLog> logs = (action != null && !action.isEmpty())
                ? auditService.getAuditLogsByAction(action)
                : auditService.getAuditLogs();
        return ResponseEntity.ok(logs);
    }
}