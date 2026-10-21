package com.nkolay.ucmd.service;

import com.nkolay.ucmd.enums.MetricStatus;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.MetricSnapshot;
import com.nkolay.ucmd.monitor.CollectionMetrics;
import com.nkolay.ucmd.repository.SnapshotRepository;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

@Service
@EnableScheduling
public class SnapshotScheduler {

    private final ExecutorService virtualThreadExecutor;

    private final MonitoringService monitoringService;
    private final AuditService auditService;
    private final CollectionMetrics collectionMetrics;

    private final SnapshotRepository snapshotRepository;

    public SnapshotScheduler(ExecutorService virtualThreadExecutor, MonitoringService monitoringService, AuditService auditService, CollectionMetrics collectionMetrics, SnapshotRepository snapshotRepository) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.monitoringService = monitoringService;
        this.auditService = auditService;
        this.collectionMetrics = collectionMetrics;
        this.snapshotRepository = snapshotRepository;
    }

    public List<MetricSnapshot> getSnapshots() {
        return snapshotRepository.findAll();
    }

    public List<MetricSnapshot> getSnapshotsForInstance(Long instanceId) {
        return snapshotRepository.findByInstanceId(instanceId);
    }
    private static final Logger logger = LoggerFactory.getLogger(SnapshotScheduler.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);



    @PreDestroy
    public void onShutdown() {
        logger.info("SnapshotScheduler shutting down, stopping metric collection");
        shuttingDown.set(true);
    }

    @Scheduled(fixedRate = 30000)
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
                                long start = System.nanoTime();
                                try {
                                    Long id = instance.getId();
                                    MetricSnapshot snapshot = new MetricSnapshot();
                                    snapshot.setInstanceId(id);

                                    // 1) Önce sağlık/erişilebilirlik
                                    HealthInfo health = monitoringService.getInstanceHealth(id);

                                    if (health == null || "DOWN".equalsIgnoreCase(health.getStatus())) {
                                        // Erişilemiyor → DOWN snapshot yaz, gerisini atla
                                        snapshot.setStatus("DOWN");
                                        snapshot.setVersion(health != null ? health.getVersion() : "unknown");
                                        snapshot.setUptimeSeconds(0);
                                        snapshot.setOverallStatus(MetricStatus.UNAVAILABLE);
                                        currentSnapshots.add(snapshot);
                                        collectionMetrics.recordCollectionFailure(instance.getName());
                                        logger.debug("Instance {} is DOWN, recorded DOWN snapshot", instance.getName());
                                        return;
                                    }

                                    // 2) UP → gerçek metrikleri topla
                                    snapshot.setStatus("UP");
                                    snapshot.setVersion(health.getVersion());
                                    snapshot.setUptimeSeconds(health.getUptimeSeconds());
                                    snapshot.setOverallStatus(MetricStatus.AVAILABLE);

                                    ResourceStats resources = monitoringService.getResourceStats(id);
                                    if (resources != null) {
                                        snapshot.setCpuPercent(resources.getCpuUsagePercent());
                                        snapshot.setMemoryUsed(resources.getMemoryUsedBytes());
                                        snapshot.setMemoryMax(resources.getMemoryMaxBytes());
                                        snapshot.setFragmentationRatio(resources.getFragmentationRatio());
                                    }

                                    DataStats data = monitoringService.getDataStats(id);
                                    if (data != null) {
                                        snapshot.setKeyCount(data.getTotalKeyCount());
                                    }

                                    ConnectionStats connections = monitoringService.getConnectionStats(id);
                                    if (connections != null) {
                                        snapshot.setClientCount(connections.getActiveClients());
                                        snapshot.setConnectionErrors(connections.getTotalConnectionErrors());
                                    }

                                    CacheEfficiencyStats efficiency =
                                            monitoringService.getCacheEfficiency(id);
                                    if (efficiency != null) {
                                        snapshot.setHitRatio(efficiency.getHitRatio());
                                        snapshot.setOpsPerSec(
                                                efficiency.getInstantaneousOpsPerSec());
                                    }

                                    if (data != null) {
                                        snapshot.setEvictedKeys(data.getEvictedKeys());
                                        snapshot.setExpiredKeys(data.getExpiredKeys());
                                    }

                                    ReplicationInfo replication =
                                            monitoringService.getReplicationInfo(id);
                                    if (replication != null) {
                                        snapshot.setReplicationRole(replication.getRole());
                                        snapshot.setConnectedReplicas(
                                                replication.getConnectedReplicas());
                                    }

                                    // Hazelcast ise cluster bilgisi (Redis'te null döner)
                                    ClusterInfo cluster = monitoringService.getClusterInfo(id);
                                    if (cluster != null) {
                                        snapshot.setNodeCount(cluster.getNodeCount());
                                    }

                                    currentSnapshots.add(snapshot);
                                    collectionMetrics.recordCollectionSuccess(instance.getName(),
                                            (System.nanoTime() - start) / 1_000_000);
                                    logger.debug("Collected real metrics for instance: {}", instance.getName());

                                } catch (Exception e) {
                                    collectionMetrics.recordCollectionFailure(instance.getName());
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

            snapshotRepository.saveAll(currentSnapshots);

            logger.debug("Metrics collected from {} instances", currentSnapshots.size());

        } catch (Exception e) {
            auditService.logError("METRICS_COLLECTION_FAILED", e.getMessage());
        }
    }
    public List<MetricSnapshot> getSnapshotsForInstance(Long instanceId, Integer lastMinutes) {
        if (lastMinutes != null && lastMinutes > 0) {
            LocalDateTime after = LocalDateTime.now().minusMinutes(lastMinutes);
            return snapshotRepository
                    .findByInstanceIdAndCollectedAtAfterOrderByCollectedAtAsc(instanceId, after);
        }
        return snapshotRepository.findByInstanceIdOrderByCollectedAtAsc(instanceId);
    }
}