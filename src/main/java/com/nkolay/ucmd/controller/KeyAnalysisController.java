package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.dto.KeyInfo;
import com.nkolay.ucmd.model.dto.KeyPatternStats;
import com.nkolay.ucmd.model.dto.PageInfo;
import com.nkolay.ucmd.service.KeyAnalysisService;
import com.nkolay.ucmd.model.dto.KeyTtlResponse;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/v1/instances")
public class KeyAnalysisController {

    @Autowired
    private KeyAnalysisService keyAnalysisService;

    @Autowired
    private MonitoringService monitoringService;

    @GetMapping("/{id}/keys")
    public ResponseEntity<PageInfo<KeyInfo>> scanKeys(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String prefix,
            @RequestParam(required = false, defaultValue = "0") String cursor,
            @RequestParam(required = false, defaultValue = "20") int count) {

        if (prefix.length() > 512) {
            return ResponseEntity.badRequest().build();
        }

        PageInfo<KeyInfo> result = keyAnalysisService.scanKeys(id, prefix, cursor, count);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/keys/top")
    public ResponseEntity<List<KeyInfo>> getTopNKeys(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "20") int n) {

        if (n > 1000) {
            return ResponseEntity.badRequest().build();
        }

        List<KeyInfo> topKeys = keyAnalysisService.getTopNKeys(id, n);
        return ResponseEntity.ok(topKeys);
    }

    @GetMapping("/{id}/keys/ttl")
    public ResponseEntity<KeyTtlResponse> getKeyTTL(
            @PathVariable Long id,
            @RequestParam String key) {
        if (key == null || key.isEmpty() || key.length()>512) {
            return ResponseEntity.badRequest().build();
        }

        long ttlMillis = keyAnalysisService.getKeyTTL(id, key);
        return ResponseEntity.ok(new KeyTtlResponse(key, ttlMillis));

    }

    @GetMapping("/{id}/key-patterns")
    public ResponseEntity<List<KeyPatternStats>> getKeyPatternStats(@PathVariable Long id) {
        return ResponseEntity.ok(monitoringService.getKeyPatternStats(id));
    }

}
