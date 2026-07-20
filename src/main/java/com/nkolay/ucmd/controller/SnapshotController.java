package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.MetricSnapshot;
import com.nkolay.ucmd.service.SnapshotScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/instances")
public class SnapshotController {

    @Autowired
    private SnapshotScheduler snapshotScheduler;

    @GetMapping("/{id}/snapshots")
    public ResponseEntity<List<MetricSnapshot>> getSnapshots(@PathVariable Long id) {
        return ResponseEntity.ok(snapshotScheduler.getSnapshotsForInstance(id));
    }
}