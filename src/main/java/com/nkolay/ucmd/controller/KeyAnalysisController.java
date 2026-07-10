package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.KeyInfo;
import com.nkolay.ucmd.model.PageInfo;
import com.nkolay.ucmd.service.KeyAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/instances")
public class KeyAnalysisController {

    @Autowired
    private KeyAnalysisService keyAnalysisService;

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
}
