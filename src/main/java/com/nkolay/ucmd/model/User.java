package com.nkolay.ucmd.model;

import java.security.SecureRandom;

public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String role;
    private boolean active;
    private long createdAt;
    private long lastLoginAt;

    public User() {}

    public User (String username, String passwordHash, String email, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
        this.role = role;
        this.active = true;
        this.createdAt = System.currentTimeMillis();

    }

    public Long getId() {return id; }
    public void setId(Long id) {this.id = id;}


    public String getUsername() {return username; }
    public void setUsername(String username) {this.username = username;}


    public String getPasswordHash() {return passwordHash; }
    public void setPasswordHash(String passwordHash) {this.passwordHash = passwordHash;}


    public String getEmail() {return email; }
    public void setEmail(String email) {this.email = email;}


    public String getRole() {return role; }
    public void setRole(String role) {this.role = role;}


    public boolean isActive() {return active; }
    public void setActive(boolean active) {this.active = active;}


    public long getCreatedAt()  {return createdAt; }
    public void setCreatedAt(long createdAt) {this.createdAt = createdAt;}

    public long getLastLoginAt() {return lastLoginAt;}
    public void setLastLoginAt(long lastLoginAt) {this.lastLoginAt = lastLoginAt;}
}
