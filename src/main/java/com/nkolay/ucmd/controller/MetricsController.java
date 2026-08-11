package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Metrics", description = "Instance metrik uçları (salt okunur)")
@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class MetricsController {

    private final MonitoringService monitoringService;

    public MetricsController(MonitoringService monitoringService) {this.monitoringService = monitoringService;}

    @Operation(summary = "Instance sağlık durumu",
               description = "Instance'ın UP/DOWN durumu, versiyon ve uptime bilgisi.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/health")
    public ResponseEntity<HealthInfo> getInstanceHealth(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getInstanceHealth(id));
    }

    @Operation(summary = "CPU ve bellek kullanımı",
               description = "Rehber §4.1/§4.2 — CPU yüzdesi, kullanılan/azami bellek, "
                       + "fragmentation. Redis'te CPU ilk turda NOT_READY döner.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/resources")
    public ResponseEntity<ResourceStats> getResourceStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getResourceStats(id));
    }

    @Operation(summary = "Bağlantı istatistikleri",
               description = "Aktif istemci sayısı, bloklu istemciler, maxclients "
                       + "ve reddedilen bağlantılar.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/connections")
    public ResponseEntity<ConnectionStats> getConnectionStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getConnectionStats(id));
    }

    @Operation(summary = "Veri istatistikleri",
               description = "Rehber §4.3 — toplam key sayısı, database bazlı dağılım, "
                       + "TTL özeti, eviction/expiration sayaçları.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/data-stats")
    public ResponseEntity<DataStats> getDataStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getDataStats(id));
    }

    @Operation(summary = "Cache verimliliği",
               description = "Rehber §4.4 — hit/miss oranı, ops/sec, ağ I/O. "
                       + "Hiç istek yoksa hitRatio null döner.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/efficiency")
    public ResponseEntity<CacheEfficiencyStats> getCacheEfficiency(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getCacheEfficiency(id));
    }

    @Operation(summary = "Replikasyon durumu",
               description = "Rehber §4.5 — master/replica rolü, bağlı replica'lar, "
                       + "replikasyon gecikmesi. Hazelcast'te NOT_APPLICABLE.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/replication")
    public ResponseEntity<ReplicationInfo> getReplicationInfo(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getReplicationInfo(id));
    }

    @Operation(summary = "Kalıcılık durumu",
               description = "Rehber §4.6 — RDB/AOF durumu, son bgsave sonucu. "
                       + "Hazelcast'te NOT_APPLICABLE (Hot Restart enterprise özelliğidir).")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/persistence")
    public ResponseEntity<PersistenceInfo> getPersistenceInfo(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getPersistenceInfo(id));
    }

    @Operation(summary = "Hazelcast map istatistikleri",
               description = "Rehber §5.3 — IMap sayısı ve entry dağılımı. "
                       + "Redis'te NOT_APPLICABLE. Bellek maliyeti client-only modda alınamaz.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/map-stats")
    public ResponseEntity<MapStatsInfo> getMapStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getMapStats(id));
    }

    @Operation(summary = "Operation ve event metrikleri",
               description = "Rehber §5.5 — Hazelcast operation queue, invocation, "
                       + "event queue ve internal executor durumu. Cluster'ın yoğunluk "
                       + "altında olup olmadığını gösterir. Redis'te N/A.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/operation-stats")
    public ResponseEntity<OperationStatsInfo> getOperationStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getOperationStats(id));
    }
}
