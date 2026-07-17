
package com.nkolay.ucmd.service;

import com.nkolay.ucmd.service.impl.AuditServiceImpl;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.nkolay.ucmd.model.AuditLog;

@Service
public interface AuditService {

    void log(String action, String detail);
    void logError(String action, String detail);
    void log(String action, String username, String detail, String result);
    List<AuditLog> getAuditLogs();
    List<AuditLog> getAuditLogsByAction(String action);

}
 