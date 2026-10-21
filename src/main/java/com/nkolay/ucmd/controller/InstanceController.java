package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Tag(name = "Instances", description = "İzlenen instance kayıtlarının okunması")
@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class InstanceController {

    private final MonitoringService monitoringService;

    public InstanceController(MonitoringService monitoringService) {this.monitoringService = monitoringService;}

    @Operation(summary = "Instance listesi",
               description = "Kayıtlı tüm Redis ve Hazelcast instance'larını döner.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @PostMapping("/list")
    public ResponseEntity<List<InstanceRef>> getAllInstances() {
        List<InstanceRef> instances = monitoringService.getAllInstances();

        return ResponseEntity.ok(instances);
    }

    @Operation(summary = "Instance detayı",
               description = "Tek bir instance'ın kayıt bilgisi (tip, host, port, cluster adı).")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @PostMapping("/{id}/detail")
    public ResponseEntity<InstanceRef> getInstanceById(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getInstanceById(id));
    }
}
