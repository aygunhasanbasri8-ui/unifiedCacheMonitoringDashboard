package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class InstanceController {

    @Autowired
    private MonitoringService monitoringService;

    @PostMapping("/list")
    public ResponseEntity<List<InstanceRef>> getAllInstances() {
        List<InstanceRef> instances = monitoringService.getAllInstances();

        return ResponseEntity.ok(instances);
    }

    @PostMapping("/{id}/detail")
    public ResponseEntity<InstanceRef> getInstanceById(@PathVariable Long id) {
        InstanceRef instance = monitoringService.getInstanceById(id);
        if (instance != null) {
            return ResponseEntity.ok(instance);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<InstanceRef> createInstance(@RequestBody InstanceRef instance) {
        InstanceRef created = monitoringService.createInstance(instance);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InstanceRef> updateInstance(
            @PathVariable Long id,
            @RequestBody InstanceRef instance) {
        instance.setId(id);
        InstanceRef updated = monitoringService.updateInstance(instance);
        if (updated != null) {
            return ResponseEntity.ok(updated);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInstance(@PathVariable Long id) {
        monitoringService.deleteInstance(id);
        return ResponseEntity.noContent().build();
    }
}
