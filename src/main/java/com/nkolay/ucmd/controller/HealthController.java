package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.CollectionHealthInfo;
import com.nkolay.ucmd.model.dto.HealthInfo;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.model.entity.MetricSnapshot;
import com.nkolay.ucmd.monitor.CollectionMetrics;
import com.nkolay.ucmd.repository.SnapshotRepository;
import com.nkolay.ucmd.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.nkolay.ucmd.enums.InstanceStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Health", description = "UCMD uygulamasının kendi sağlığı (kimlik doğrulama gerekmez)")
@RestController
@RequestMapping("/v1/health")
public class HealthController {

    private final long startedAt = System.currentTimeMillis();

    
    private final MonitoringService monitoringService;

    
    private final SnapshotRepository snapshotRepository;

    
    private final CollectionMetrics collectionMetrics;

    public HealthController(MonitoringService monitoringService, SnapshotRepository snapshotRepository, CollectionMetrics collectionMetrics) {
        this.monitoringService = monitoringService;
        this.snapshotRepository = snapshotRepository;
        this.collectionMetrics = collectionMetrics;
    }

    @Operation(summary = "Uygulama sağlığı",
               description = "UCMD'nin kendi durumu, versiyonu ve gerçek çalışma süresi.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @GetMapping
    public HealthInfo getHealth() {
        long uptimeSeconds = (System.currentTimeMillis() - startedAt) / 1000;
        return new HealthInfo("UP", "1.0.0", uptimeSeconds);
    }

    @Operation(summary = "Uygulama bilgisi", description = "Basit canlılık yanıtı.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @GetMapping("/app")
    public Map<String, Object> getAppHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "UCMD application is running");
        response.put("timestamp", System.currentTimeMillis());
        response.put("version", "1.0.0");
        return response;
    }

    /** Rehber §6 — son toplama turunun kalite özeti. */
    @Operation(summary = "Toplama sağlığı",
               description = "Rehber §6 — son toplama turunun kalite özeti: OK/PARTIAL/FAILED, "
                       + "bayatlık süresi ve erişilemeyen instance listesi.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @GetMapping("/collection")
    public CollectionHealthInfo getCollectionHealth() {
        CollectionHealthInfo info = new CollectionHealthInfo();

        List<InstanceRef> instances = monitoringService.getAllInstances();
        List<String> unavailable = new ArrayList<>();
        int healthy = 0;
        Instant latest = null;
        long maxStaleAge = 0;

        for (InstanceRef instance : instances) {
            MetricSnapshot last = snapshotRepository
                    .findFirstByInstanceIdOrderByCollectedAtDesc(instance.getId())
                    .orElse(null);

            if (last == null || !"UP".equalsIgnoreCase(last.getStatus())) {
                unavailable.add(instance.getName());
            } else {
                healthy++;
            }

            if (last != null && last.getCollectedAt() != null) {
                Instant at = last.getCollectedAt().atZone(ZoneId.systemDefault()).toInstant();
                if (latest == null || at.isAfter(latest)) latest = at;
            }

            long age = collectionMetrics.staleAgeSeconds(instance.getName());
            if (age > maxStaleAge) maxStaleAge = age;
        }

        info.setTotalInstances(instances.size());
        info.setHealthyInstances(healthy);
        info.setUnavailableInstances(unavailable);
        info.setCollectedAt(latest);
        info.setStaleAgeSeconds(maxStaleAge);

        // Toplama 30 sn'de bir çalışır — 2 tur kaçırıldıysa bayat sayılır
        info.setStale(maxStaleAge > 90);

        if (latest != null) {
            info.setCollectionDurationMs(
                    Math.max(0, System.currentTimeMillis() - latest.toEpochMilli()));
        }

        if (instances.isEmpty()) {
            info.setStatus(InstanceStatus.NOT_CONFIGURED);
        } else if ( healthy == 0) {
            info.setStatus(InstanceStatus.FAILED);
        }
        else if (!unavailable.isEmpty()) {
            info.setStatus(InstanceStatus.PARTIAL);
        } else {
            info.setStatus(InstanceStatus.OK);
        }

        return info;
    }
}
