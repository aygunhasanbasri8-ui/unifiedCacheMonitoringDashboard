package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.HistoryResponse;
import com.nkolay.ucmd.model.entity.MetricSnapshot;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.HistoryService;
import com.nkolay.ucmd.service.MonitoringService;
import com.nkolay.ucmd.service.SnapshotScheduler;
import com.nkolay.ucmd.model.dto.SnapshotQueryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@Tag(name = "Snapshots", description = "Zaman serisi metrik geçmişi (salt okunur)")
@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class SnapshotController {

    private final SnapshotScheduler snapshotScheduler;

    private final HistoryService historyService;

    private final MonitoringService monitoringService;

    public SnapshotController(SnapshotScheduler snapshotScheduler, HistoryService historyService, MonitoringService monitoringService) {
        this.snapshotScheduler = snapshotScheduler;
        this.historyService = historyService;
        this.monitoringService = monitoringService;
    }

    @Operation(summary = "Ham snapshot listesi",
               description = "Belirtilen süre içindeki tüm snapshot kayıtları. "
                       + "Grafik için /history ucunu tercih edin — bu uç örnekleme yapmaz.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @PostMapping("/{id}/snapshots")
    public ResponseEntity<List<MetricSnapshot>> getSnapshots(
            @PathVariable Long id,
            @RequestBody(required = false) SnapshotQueryRequest request) {

        // Olmayan instance için 404 (önce 200 + boş liste dönüyordu)
        monitoringService.getInstanceById(id);

        Integer lastMinutes = (request != null) ? request.getLastMinutes() : null;
        return ResponseEntity.ok(
                snapshotScheduler.getSnapshotsForInstance(id, lastMinutes));
    }

    @Operation(summary = "Örneklenmiş metrik geçmişi",
               description = "Grafik için hazırlanmış zaman serisi. Nokta sayısı maxPoints'i "
                       + "aşarsa stride örnekleme uygulanır (resolution: DOWNSAMPLED). "
                       + "Varsayılan 500, tavan 2000 nokta.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @PostMapping("/{id}/history")
    public ResponseEntity<HistoryResponse> getHistory(
            @PathVariable Long id,
            @RequestBody(required = false) SnapshotQueryRequest request) {

        SnapshotQueryRequest req = (request != null) ? request : new SnapshotQueryRequest();
        return ResponseEntity.ok(
                historyService.getHistory(id, req.getLastMinutes(), req.getMaxPoints()));
    }
}
