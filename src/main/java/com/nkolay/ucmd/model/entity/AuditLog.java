package com.nkolay.ucmd.model.entity;

import java.time.LocalDateTime;

public class AuditLog {
    private String action;
    private String detail;
    private String username;
    private LocalDateTime timestamp;
    private String result;

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