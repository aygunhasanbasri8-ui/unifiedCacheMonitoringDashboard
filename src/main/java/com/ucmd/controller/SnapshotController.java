package com.ucmd.controller;

import com.ucmd.model.dto.CapacityForecast;
import com.ucmd.model.dto.CapacityForecastRequest;
import com.ucmd.model.dto.HistoryResponse;
import com.ucmd.model.entity.MetricSnapshot;
import com.ucmd.security.RequireRole;
import com.ucmd.service.CapacityForecastService;
import com.ucmd.service.HistoryService;
import com.ucmd.service.MonitoringService;
import com.ucmd.model.dto.SnapshotQueryRequest;
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

    private final HistoryService historyService;

    private final MonitoringService monitoringService;

    private final CapacityForecastService capacityForecastService;

    public SnapshotController(HistoryService historyService, MonitoringService monitoringService,
                              CapacityForecastService capacityForecastService) {
        this.historyService = historyService;
        this.monitoringService = monitoringService;
        this.capacityForecastService = capacityForecastService;
    }

    @Operation(summary = "Bellek kapasite tahmini",
               description = "Son windowHours saatin (varsayılan 24) bellek kullanımına doğrusal eğilim uydurur, "
                       + "bu hızla bellek sınırına kaç gün kaldığını döner. Yeterli veri yoksa NOT_READY, "
                       + "sınır tanımsızsa (Redis maxmemory=0) NOT_APPLICABLE.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @PostMapping("/{id}/capacity-forecast")
    public ResponseEntity<CapacityForecast> getCapacityForecast(
            @PathVariable Long id,
            @RequestBody(required = false) CapacityForecastRequest request) {
        monitoringService.getInstanceById(id);
        Integer windowHours = request != null ? request.getWindowHours() : null;
        return ResponseEntity.ok(capacityForecastService.forecast(id, windowHours));
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
                historyService.getSnapshots(id, lastMinutes));
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
