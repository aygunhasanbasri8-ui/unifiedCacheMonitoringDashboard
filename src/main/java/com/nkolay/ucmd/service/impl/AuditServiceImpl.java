package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.entity.AuditLog;
import com.nkolay.ucmd.repository.AuditRepository;
import com.nkolay.ucmd.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditRepository auditRepository;
    private final int retentionDays;

    public AuditServiceImpl(AuditRepository auditRepository,
                            @Value("${ucmd.audit.retention-days:90}") int retentionDays) {
        this.auditRepository = auditRepository;
        this.retentionDays = retentionDays > 0 ? retentionDays : 90;
    }

    @Override
    public void log(String action, String detail) {
        persist(new AuditLog(action, detail, "SYSTEM", LocalDateTime.now(), "SUCCESS"));
    }

    @Override
    public void logError(String action, String detail) {
        persist(new AuditLog(action, detail, "SYSTEM", LocalDateTime.now(), "ERROR"));
    }

    @Override
    public void log(String action, String username, String detail, String result) {
        persist(new AuditLog(action, detail, username, LocalDateTime.now(), result));
    }

    @Override
    public void log(String action, String username, String detail,
                    String result, String ipAddress) {
        AuditLog entry = new AuditLog(action, detail, username, LocalDateTime.now(), result);
        entry.setIpAddress(ipAddress);
        persist(entry);
    }

    @Override
    public Page<AuditLog> getAuditLogs(Pageable pageable) {
        return auditRepository.findByOrderByTimestampDesc(pageable);
    }

    @Override
    public Page<AuditLog> getAuditLogsByAction(String action, Pageable pageable) {
        return auditRepository.findByActionOrderByTimestampDesc(action, pageable);
    }

    @Override
    public Page<AuditLog> getAuditLogsByUsername(String username, Pageable pageable) {
        return auditRepository.findByUsernameOrderByTimestampDesc(username, pageable);
    }

    private void persist(AuditLog entry) {
        try {
            auditRepository.save(entry);
        } catch (Exception e) {
            logger.error("Audit kaydı yazılamadı: {}", e.getMessage());
            // Yutuluyor — audit hatası ana işlemi kesmemeli
        }
    }

    @Scheduled(cron = "${ucmd.audit.retention-cron:0 0 3 * * *}")
    public void cleanupOldEntries() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            long deleted = auditRepository.deleteByTimestampBefore(cutoff);
            if (deleted > 0) {
                logger.info("Audit temizliği: {} kayıt silindi ({} günden eski)",
                        deleted, retentionDays);
            }
        } catch (Exception e) {
            logger.error("Audit temizliği başarısız: {}", e.getMessage());
        }
    }
}
