package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.MetricSnapshot;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.SnapshotScheduler;
import com.nkolay.ucmd.model.dto.SnapshotQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class SnapshotController {

    @Autowired
    private SnapshotScheduler snapshotScheduler;

    @PostMapping("/{id}/snapshots")
    public ResponseEntity<List<MetricSnapshot>> getSnapshots(
            @PathVariable Long id,
            @RequestBody(required = false) SnapshotQueryRequest request) {
        Integer lastMinutes = (request != null) ? request.getLastMinutes() : null;
        return ResponseEntity.ok(
                snapshotScheduler.getSnapshotsForInstance(id, lastMinutes));
    }
}