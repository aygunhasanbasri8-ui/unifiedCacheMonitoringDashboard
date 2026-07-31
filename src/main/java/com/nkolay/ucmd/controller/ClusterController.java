package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.ClusterInfo;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class ClusterController {

    @Autowired
    private MonitoringService monitoringService;

    @PostMapping("/{id}/cluster")
    public ResponseEntity<ClusterInfo> getClusterInfo(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getClusterInfo(id));
    }
}