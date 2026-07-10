
package com.nkolay.ucmd.service;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

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

    // Inner class
    public static class AuditLog {
        private String action;
        private String detail;
        private String username;
        private LocalDateTime timestamp;
        private String result;  // SUCCESS | DENIED | ERROR

        public AuditLog(String action, String detail, String username, LocalDateTime timestamp, String result) {
            this.action = action;
            this.detail = detail;
            this.username = username;
            this.timestamp = timestamp;
            this.result = result;
        }

        public String getAction() { return action; }
        public String getDetail() { return detail; }
        public String getUsername() { return username; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public String getResult() { return result; }
    }
}
 