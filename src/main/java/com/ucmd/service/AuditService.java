
package com.ucmd.service;

import com.ucmd.model.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditService {

    void log(String action, String detail);

    void logError(String action, String detail);

    void log(String action, String username, String detail, String result);

    void log(String action, String username, String detail, String result, String ipAddress);

    Page<AuditLog> getAuditLogs(Pageable pageable);

    Page<AuditLog> getAuditLogsByAction(String action, Pageable pageable);

    Page<AuditLog> getAuditLogsByUsername(String username, Pageable pageable);
}
