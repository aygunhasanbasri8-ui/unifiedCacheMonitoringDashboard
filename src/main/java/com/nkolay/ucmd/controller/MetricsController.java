package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.HealthInfo;
import com.nkolay.ucmd.model.dto.ResourceStats;
import com.nkolay.ucmd.model.dto.ConnectionStats;
import com.nkolay.ucmd.model.dto.DataStats;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/instances")
public class MetricsController {

    @Autowired
    private MonitoringService monitoringService;

    @GetMapping("/{id}/health")
    public ResponseEntity<HealthInfo> getInstanceHealth(@PathVariable Long id) {
        HealthInfo health = monitoringService.getInstanceHealth(id);
        if (health != null) {
            return ResponseEntity.ok(health);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/resources")
    public ResponseEntity<ResourceStats> getResourceStats(@PathVariable Long id) {
        ResourceStats stats = monitoringService.getResourceStats(id);
        if (stats != null) {
            return ResponseEntity.ok(stats);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/connections")
    public ResponseEntity<ConnectionStats> getConnectionStats(@PathVariable Long id) {
        ConnectionStats stats = monitoringService.getConnectionStats(id);
        if (stats != null) {
            return ResponseEntity.ok(stats);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/data-stats")
    public ResponseEntity<DataStats> getDataStats(@PathVariable Long id) {
        DataStats stats = monitoringService.getDataStats(id);
        if (stats != null) {
            return ResponseEntity.ok(stats);
        }
        return ResponseEntity.notFound().build();
    }
}
