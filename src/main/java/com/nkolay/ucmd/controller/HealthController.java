package com.nkolay.ucmd.controller;

import com.nkolay.ucmd.model.HealthInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public HealthInfo getHealth() {
        return new HealthInfo("UP", "1.0.0", System.currentTimeMillis() / 1000);
    }

    @GetMapping("/app")
    public Map<String, Object> getAppHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "UCMD application is running");
        response.put("timestamp", System.currentTimeMillis());
        response.put("version", "1.0.0");
        return response;
    }
}
