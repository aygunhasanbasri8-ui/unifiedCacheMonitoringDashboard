package com.ucmd.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_username", columnList = "username"),
    @Index(name = "idx_audit_action", columnList = "action")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 1000)
    private String detail;

    @Column(length = 128)
    private String username;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 32)
    private String result;       // SUCCESS | DENIED | ERROR

    @Column(length = 64)
    private String ipAddress;

    /** JPA için zorunlu. */
    public AuditLog() {}

    public AuditLog(String action, String detail, String username,
                    LocalDateTime timestamp, String result) {
        this.action = action;
        this.detail = detail;
        this.username = username;
        this.timestamp = timestamp;
        this.result = result;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
