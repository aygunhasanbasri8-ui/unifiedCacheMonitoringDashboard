package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.MetricSnapshot;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

@Service
@EnableScheduling
public class SnapshotScheduler {

    @Autowired
    private ExecutorService virtualThreadExecutor;

    @Autowired
    private MonitoringService monitoringService;

    @Autowired
    private AuditService auditService;

    public List<MetricSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    public List<MetricSnapshot> getSnapshotsForInstance(Long instanceId) {
        return snapshots.stream()
                .filter(s -> s.getInstanceId().equals(instanceId))
                .toList();
    }

    private static final Logger logger = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    // DÜZELTME 1: Performanslı silme işlemi için LinkedList ve Thread-Safe yapı
    private final List<MetricSnapshot> snapshots = Collections.synchronizedList(new java.util.LinkedList<>());
    private static final int MAX_SNAPSHOT_HISTORY = 1000; // Maksimum tutulacak kayıt sayısı

    @PreDestroy
    public void onShutdown() {
        logger.info("SnapshotScheduler shutting down, stopping metric collection");
        shuttingDown.set(true);
    }

    @Scheduled(fixedRate = 15000)
    public void collectMetrics() {

        if (shuttingDown.get()) {
            logger.info("Skipping metric collection — shutdown in progress");
            return;
        }

        try {
            List<MetricSnapshot> currentSnapshots = Collections.synchronizedList(new ArrayList<>());

            List<java.util.concurrent.Future<?>> futures = new ArrayList<>(
                    monitoringService.getAllInstances().stream()
                            .map(instance -> (java.util.concurrent.Future<?>) virtualThreadExecutor.submit(() -> {
                                try {
                                    MetricSnapshot snapshot = new MetricSnapshot();
                                    snapshot.setInstanceId(instance.getId());
                                    snapshot.setStatus("UP");
                                    snapshot.setVersion("1.0.0");
                                    snapshot.setUptimeSeconds(System.currentTimeMillis() / 1000);
                                    snapshot.setCpuPercent(Math.random() * 100);
                                    snapshot.setMemoryUsed((long) (Math.random() * 1024000000));
                                    snapshot.setMemoryMax(1024000000L);
                                    snapshot.setKeyCount((long) (Math.random() * 50000));
                                    snapshot.setClientCount((int) (Math.random() * 100));
                                    snapshot.setConnectionErrors(0);
                                    snapshot.setFragmentationRatio(1.0 + Math.random() * 0.5);

                                    currentSnapshots.add(snapshot);
                                    // DÜZELTME 2: snapshots.add(snapshot) buradan kaldırıldı (Race condition önlendi)

                                    logger.debug("Collected metrics for instance: {}", instance.getName());
                                } catch (Exception e) {
                                    logger.error("Failed to collect metrics for {}: {}", instance.getName(), e.getMessage());
                                }
                            }))
                            .toList()
            );

            for (java.util.concurrent.Future<?> future : futures) {
                try {
                    future.get(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    logger.warn("Metric collection timeout for one instance: {}", e.getMessage());
                }
            }

            // DÜZELTME 3: Ana akışta toplu ekleme ve Memory Leak önlemi
            synchronized (snapshots) {
                snapshots.addAll(currentSnapshots);

                // Kapasite aşımı varsa en eski kayıtları (listenin başından) sil
                int excess = snapshots.size() - MAX_SNAPSHOT_HISTORY;
                if (excess > 0) {
                    snapshots.subList(0, excess).clear();
                }
            }

            auditService.log("METRICS_COLLECTED", "Collected metrics from " + currentSnapshots.size() + " instances");

        } catch (Exception e) {
            auditService.logError("METRICS_COLLECTION_FAILED", e.getMessage());
        }
    }
}