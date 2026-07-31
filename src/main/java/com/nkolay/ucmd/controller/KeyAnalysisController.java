package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.KeyInfo;
import com.nkolay.ucmd.model.dto.KeyPatternStats;
import com.nkolay.ucmd.model.dto.PageInfo;
import com.nkolay.ucmd.security.RequireRole;
import com.nkolay.ucmd.service.KeyAnalysisService;
import com.nkolay.ucmd.model.dto.KeyTtlResponse;
import com.nkolay.ucmd.service.MonitoringService;
import com.nkolay.ucmd.model.dto.SearchKeysRequest;
import com.nkolay.ucmd.model.dto.TopKeysRequest;
import com.nkolay.ucmd.model.dto.KeyTtlRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/instances")
public class KeyAnalysisController {

    @Autowired
    private KeyAnalysisService keyAnalysisService;

    @Autowired
    private MonitoringService monitoringService;

    @PostMapping("/{id}/keys")
    public ResponseEntity<PageInfo<KeyInfo>> scanKeys(
            @PathVariable Long id,
            @RequestBody(required = false) SearchKeysRequest request) {

        SearchKeysRequest req = (request != null) ? request : new SearchKeysRequest();
        String prefix = req.getPrefix();
        if (prefix.length() > 512) {
            throw new IllegalArgumentException("Prefix en fazla 512 karakter olabilir");
        }

        PageInfo<KeyInfo> result =
                keyAnalysisService.scanKeys(id, prefix, req.getCursor(), req.getCount());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/keys/top")
    public ResponseEntity<List<KeyInfo>> getTopNKeys(
            @PathVariable Long id,
            @RequestBody(required = false) TopKeysRequest request) {

        int n = (request!=null) ? request.getN() : 20;
        if (n > 1000) {
            throw new IllegalArgumentException("N en fazla 1000 olabilir.");
        }

        List<KeyInfo> topKeys = keyAnalysisService.getTopNKeys(id, n);
        return ResponseEntity.ok(topKeys);
    }

    @PostMapping("/{id}/keys/ttl")
    public ResponseEntity<KeyTtlResponse> getKeyTTL(
            @PathVariable Long id,
            @RequestBody KeyTtlRequest request){

        String key = (request!=null) ? request.getKey() : null;
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key boş olamaz.");
        }

        if (key.length()>512) {
            throw new IllegalArgumentException("Key uzunluğu 512'swn büyük olamaz.");

        }

        long ttlMillis = keyAnalysisService.getKeyTTL(id, key);
        return ResponseEntity.ok(new KeyTtlResponse(key, ttlMillis));

    }

    @PostMapping("/{id}/key-patterns")
    public ResponseEntity<List<KeyPatternStats>> getKeyPatternStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getKeyPatternStats(id));
    }

}
