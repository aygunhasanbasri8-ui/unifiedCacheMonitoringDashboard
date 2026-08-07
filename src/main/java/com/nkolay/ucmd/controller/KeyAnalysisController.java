package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.security.AuditContext;
import com.nkolay.ucmd.security.KeyMasker;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.AuditService;
import com.nkolay.ucmd.service.KeyAnalysisService;
import com.nkolay.ucmd.service.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "Key Analysis", description = "Key tarama ve analiz uçları (SCAN tabanlı, pahalı)")
@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class KeyAnalysisController {

    @Autowired
    private KeyAnalysisService keyAnalysisService;

    @Autowired
    private MonitoringService monitoringService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditContext auditContext;

    @Autowired
    private KeyMasker keyMasker;

    @Operation(summary = "Key tarama (sayfalamalı)",
               description = "Redis: cursor tabanlı SCAN, prefix zorunlu (en az 3 karakter), "
                       + "KEYS * kullanılmaz. Hazelcast: mapName boşsa IMap adları listelenir, "
                       + "doluysa o map içindeki entry key'leri örneklenerek aranır "
                       + "(sınırlı tarama, sonuç ESTIMATED). Dönen adlar maskelenir (rehber §9).")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "400", description = "Prefix eksik, çok kısa veya izin listesinde değil")
    @ApiResponse(responseCode = "403", description = "Map izin verilen listede değil")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "429", description = "Sorgu limiti aşıldı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @ApiResponse(responseCode = "504", description = "Sorgu zaman aşımına uğradı")
    @PostMapping("/{id}/keys")
    public ResponseEntity<PageInfo<KeyInfo>> scanKeys(
            @PathVariable Long id,
            @RequestBody(required = false) SearchKeysRequest request,
            HttpServletRequest httpRequest) {

        SearchKeysRequest req = (request != null) ? request : new SearchKeysRequest();
        String prefix = req.getPrefix();
        if (prefix == null || prefix.length() > 512) {
            throw new IllegalArgumentException("Prefix en fazla 512 karakter olabilir");
        }

        PageInfo<KeyInfo> result = keyAnalysisService.scanKeys(
                id, prefix, req.getCursor(), req.getCount(), req.getMatchMode(), req.getMapName());

        auditService.log("KEY_SEARCH",
                auditContext.currentUsername(httpRequest),
                "instance=" + id
                        + ", map=" + keyMasker.maskForLog(req.getMapName())
                        + ", prefix=" + keyMasker.maskForLog(prefix)
                        + ", matchMode=" + req.getMatchMode()
                        + ", results=" + result.getContent().size(),
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "En büyük N key",
               description = "Sınırlı SCAN ile en büyük key'ler. Tarama tamamlanmazsa "
                       + "sonuç ESTIMATED işaretlenir. Hazelcast'te byte boyutu alınamaz — "
                       + "sıralama entry sayısına göre yapılır.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "429", description = "Sorgu limiti aşıldı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @ApiResponse(responseCode = "504", description = "Sorgu zaman aşımına uğradı")
    @PostMapping("/{id}/keys/top")
    public ResponseEntity<TopKeysResponse> getTopNKeys(
            @PathVariable Long id,
            @RequestBody(required = false) TopKeysRequest request,
            HttpServletRequest httpRequest) {


        TopKeysRequest req = (request != null) ? request : new TopKeysRequest();
        TopKeysResponse response =
                keyAnalysisService.getTopNKeys(id, req.getN(), req.getScanLimit());

        auditService.log("KEY_TOP_N",
                auditContext.currentUsername(httpRequest),
                "instance=" + id + ", n=" + req.getN()
                        + ", scanned=" + response.getScannedCount(),
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Key TTL sorgusu",
               description = "Redis: PTTL. Hazelcast: mapName verilirse o map içindeki "
                       + "entry'nin kalan süresi, verilmezse key bir IMap adı sayılır. "
                       + "Yanıttaki key maskelenmez — denetim kaydına maskeli yazılır.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "400", description = "Key boş veya 512 karakterden uzun")
    @ApiResponse(responseCode = "403", description = "Map izin verilen listede değil")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @PostMapping("/{id}/keys/ttl")
    public ResponseEntity<KeyTtlResponse> getKeyTTL(
            @PathVariable Long id,
            @RequestBody KeyTtlRequest request,
            HttpServletRequest httpRequest){

        String key = (request!=null) ? request.getKey() : null;
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key boş olamaz.");
        }

        if (key.length()>512) {
            throw new IllegalArgumentException("Key uzunluğu 512'swn büyük olamaz.");

        }

        KeyTtlResponse response = keyAnalysisService.getKeyTTL(id, key, request.getMapName());

        auditService.log("KEY_TTL_QUERY",
                auditContext.currentUsername(httpRequest),
                "instance=" + id
                        + ", map=" + keyMasker.maskForLog(request.getMapName())
                        + ", key=" + keyMasker.maskForLog(key),
                "SUCCESS",
                auditContext.clientIp(httpRequest));

        return ResponseEntity.ok(response);

    }

    @Operation(summary = "Key desen istatistikleri",
               description = "Yapılandırmadaki desenlere göre key'leri gruplar. "
                       + "Tam SCAN gerektirir — pahalı işlem, tüm korumalar aktif.")
    @ApiResponse(responseCode = "200", description = "Başarılı")
    @ApiResponse(responseCode = "404", description = "Instance bulunamadı")
    @ApiResponse(responseCode = "429", description = "Sorgu limiti aşıldı")
    @ApiResponse(responseCode = "503", description = "Instance erişilemez veya meşgul")
    @ApiResponse(responseCode = "504", description = "Sorgu zaman aşımına uğradı")
    @PostMapping("/{id}/key-patterns")
    public ResponseEntity<List<KeyPatternStats>> getKeyPatternStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getKeyPatternStats(id));
    }

}
