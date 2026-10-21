package com.ucmd.service;

import com.ucmd.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Snapshot tablosu sınırsız büyümesin diye günlük temizlik.
 * Saklama süresi audit'ten kısadır (7 gün) — hacim yüksek, denetim değeri yok.
 */
@Service
public class SnapshotRetentionService {

    private static final Logger logger =
            LoggerFactory.getLogger(SnapshotRetentionService.class);

    private final SnapshotRepository snapshotRepository;
    private final int retentionDays;

    public SnapshotRetentionService(SnapshotRepository snapshotRepository,
                                    @Value("${ucmd.snapshot.retention-days:7}") int retentionDays) {
        this.snapshotRepository = snapshotRepository;
        this.retentionDays = retentionDays > 0 ? retentionDays : 7;
    }

    @Scheduled(cron = "${ucmd.snapshot.retention-cron:0 30 3 * * *}")
    public void cleanup() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            long deleted = snapshotRepository.deleteByCollectedAtBefore(cutoff);
            if (deleted > 0) logger.info("Snapshot temizliği: {} kayıt silindi", deleted);
        } catch (Exception e) {
            logger.error("Snapshot temizliği başarısız: {}", e.getMessage());
        }
    }
}
