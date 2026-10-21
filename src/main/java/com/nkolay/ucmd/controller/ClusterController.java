package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.ClusterInfo;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cluster", description = "Cluster topolojisi ve partition dağılımı (salt okunur)")
@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class ClusterController {

    private final MonitoringService monitoringService;

    public ClusterController(MonitoringService monitoringService) {this.monitoringService = monitoringService;}

    @Operation(summary = "Cluster bilgisi",
               description = "Üye listesi, partition sayısı, member başına dağılım, skew yüzdesi ve cluster-safe durumu. Redis standalone'da NOT_APPLICABLE döner.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/cluster")
    public ResponseEntity<ClusterInfo> getClusterInfo(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getClusterInfo(id));
    }
}