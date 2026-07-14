package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.ClusterInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/instances")
public class ClusterController {

    @Autowired
    private MonitoringService monitoringService;

    @GetMapping("/{id}/cluster")
    public ResponseEntity<ClusterInfo> getClusterInfo(@PathVariable Long id) {
        ClusterInfo clusterInfo = monitoringService.getClusterInfo(id);
        if (clusterInfo != null) {
            return ResponseEntity.ok(clusterInfo);
        }
        return ResponseEntity.notFound().build();
    }
}