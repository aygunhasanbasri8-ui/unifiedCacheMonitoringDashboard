package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.MetricSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@EnableScheduling
public class SnapshotScheduler {

    @Autowired
    private MonitoringService monitoringService;

    @Autowired
    private AuditService auditService;

    private List<MetricSnapshot> snapshots = new ArrayList<>();

    /**
     * Her 15 saniyede bir metrik toplama
     * Gerçek implementasyonda tüm instance'lardan PARALEL çekeceğiz
     */
    @Scheduled(fixedRate = 15000)  // 15 saniye
    public void collectMetrics() {
        try {
            List<MetricSnapshot> currentSnapshots = new ArrayList<>();

            monitoringService.getAllInstances().forEach(instance -> {
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
                snapshots.add(snapshot);
            });

            auditService.log("METRICS_COLLECTED", "Collected metrics from " + currentSnapshots.size() + " instances");

        } catch (Exception e) {
            auditService.logError("METRICS_COLLECTION_FAILED", e.getMessage());
        }
    }

    public List<MetricSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    public List<MetricSnapshot> getSnapshotsForInstance(Long instanceId) {
        return snapshots.stream()
                .filter(s -> s.getInstanceId().equals(instanceId))
                .toList();
    }
}
