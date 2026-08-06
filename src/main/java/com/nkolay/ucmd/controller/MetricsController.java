package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class MetricsController {

    @Autowired
    private MonitoringService monitoringService;

    @PostMapping("/{id}/health")
    public ResponseEntity<HealthInfo> getInstanceHealth(@PathVariable Long id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/resources")
    public ResponseEntity<ResourceStats> getResourceStats(@PathVariable Long id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/connections")
    public ResponseEntity<ConnectionStats> getConnectionStats(@PathVariable Long id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/data-stats")
    public ResponseEntity<DataStats> getDataStats(@PathVariable Long id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/efficiency")
    public ResponseEntity<CacheEfficiencyStats> getCacheEfficiency(@PathVariable Long id) {
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/replication")
    public ResponseEntity<ReplicationInfo> getReplicationInfo(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getReplicationInfo(id));
    }

    @PostMapping("/{id}/persistence")
    public ResponseEntity<PersistenceInfo> getPersistenceInfo(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getPersistenceInfo(id));
    }
}
