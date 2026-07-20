package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.entity.AuditLog;
import com.nkolay.ucmd.service.AuditService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditServiceImpl implements AuditService {

    private List<AuditLog> auditLogs = new ArrayList<>();

    public void log(String action, String detail) {
        AuditLog log = new AuditLog(
                action,
                detail,
                "SYSTEM",
                LocalDateTime.now(),
                "SUCCESS"
        );
        auditLogs.add(log);
    }

    public void logError(String action, String detail) {
        AuditLog log = new AuditLog(
                action,
                detail,
                "SYSTEM",
                LocalDateTime.now(),
                "ERROR"
        );
        auditLogs.add(log);
    }

    public void log(String action, String username, String detail, String result) {
        AuditLog log = new AuditLog(action, detail, username, LocalDateTime.now(), result);
        auditLogs.add(log);
    }

    public List<AuditLog> getAuditLogs() {
        return new ArrayList<>(auditLogs);
    }

    public List<AuditLog> getAuditLogsByAction(String action) {
        return auditLogs.stream()
                .filter(log -> action.equals(log.getAction()))
                .toList();
    }

}