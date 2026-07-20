
package com.nkolay.ucmd.service;

import org.springframework.stereotype.Service;

import java.util.List;
import com.nkolay.ucmd.model.entity.AuditLog;

@Service
public interface AuditService {

    void log(String action, String detail);
    void logError(String action, String detail);
    void log(String action, String username, String detail, String result);
    List<AuditLog> getAuditLogs();
    List<AuditLog> getAuditLogsByAction(String action);

}
 