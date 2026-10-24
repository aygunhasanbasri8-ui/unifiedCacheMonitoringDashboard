package com.ucmd.controller;

import com.ucmd.config.AlertConfig;
import com.ucmd.model.dto.ActiveAlert;
import com.ucmd.model.dto.AlertHistoryRequest;
import com.ucmd.model.entity.AlertEvent;
import com.ucmd.security.RequireRole;
import com.ucmd.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Alerts", description = "Eşik alarmları: süren alarmlar, geçmiş ve kurallar (salt okunur)")
@RestController
@RequireRole("MONITOR")
@RequestMapping("/v1/alerts")
public class AlertController {

    private static final int DEFAULT_HISTORY_LIMIT = 100;

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @Operation(summary = "Şu an süren alarmlar")
    @PostMapping("/active")
    public ResponseEntity<List<ActiveAlert>> active() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    @Operation(summary = "Alarm geçmişi", description = "Başlayan ve düzelen alarmlar, yeniden eskiye. limit en fazla 500.")
    @PostMapping("/history")
    public ResponseEntity<List<AlertEvent>> history(@RequestBody(required = false) AlertHistoryRequest request) {
        Long instanceId = request != null ? request.getInstanceId() : null;
        int limit = request != null && request.getLimit() != null ? request.getLimit() : DEFAULT_HISTORY_LIMIT;
        return ResponseEntity.ok(alertService.getHistory(instanceId, limit));
    }

    @Operation(summary = "Tanımlı alarm kuralları")
    @PostMapping("/rules")
    public ResponseEntity<List<AlertConfig.Rule>> rules() {
        return ResponseEntity.ok(alertService.getRules());
    }
}
