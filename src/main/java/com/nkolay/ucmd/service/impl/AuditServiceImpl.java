package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.entity.AuditLog;
import com.nkolay.ucmd.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AuditServiceImpl implements AuditService {

    private final int maxEntries;

    private final Deque<AuditLog> auditLogs = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    public AuditServiceImpl(@Value("${ucmd.audit.max-entries:5000}") int maxEntries) {
        this.maxEntries = maxEntries > 0 ? maxEntries : 5000;
    }

    @Override
    public void log(String action, String detail) {
        append(new AuditLog(action, detail, "SYSTEM", LocalDateTime.now(), "SUCCESS"));
    }

    @Override
    public void logError(String action, String detail) {
        append(new AuditLog(action, detail, "SYSTEM", LocalDateTime.now(), "ERROR"));
    }

    @Override
    public void log(String action, String username, String detail, String result) {
        append(new AuditLog(action, detail, username, LocalDateTime.now(), result));
    }

    @Override
    public List<AuditLog> getAuditLogs() {
        lock.lock();
        try{
            return new ArrayList<>(auditLogs);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<AuditLog> getAuditLogsByAction(String action) {
        lock.lock();
        try {
            return auditLogs.stream()
                    .filter(log -> action.equals(log.getAction()))
                    .toList();
        } finally {
            lock.unlock();
        }
    }


    private void append(AuditLog entry) {
        lock.lock();
        try {
            auditLogs.addLast(entry);
            while (auditLogs.size() > maxEntries) {
                auditLogs.removeFirst();
            }
        } finally {
            lock.unlock();
        }
    }


}