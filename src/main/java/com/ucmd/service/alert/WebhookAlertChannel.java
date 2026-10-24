package com.ucmd.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucmd.config.AlertConfig;
import com.ucmd.model.entity.AlertEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Olayı olduğu gibi JSON olarak gönderir (PagerDuty köprüsü, kendi servisiniz...). */
@Component
public class WebhookAlertChannel implements AlertChannel {

    private final AlertConfig.Webhook config;
    private final ObjectMapper mapper;

    public WebhookAlertChannel(AlertConfig alertConfig, ObjectMapper mapper) {
        this.config = alertConfig.getChannels().getWebhook();
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public boolean isConfigured() {
        return !config.getUrl().isBlank();
    }

    @Override
    public void send(AlertEvent event) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("rule", event.getRuleName());
        body.put("state", event.getState().name());
        body.put("severity", event.getSeverity());
        body.put("instanceId", event.getInstanceId());
        body.put("instanceName", event.getInstanceName());
        body.put("value", event.getObservedValue());
        body.put("threshold", event.getThreshold());
        body.put("message", event.getMessage());
        body.put("occurredAt", event.getOccurredAt().toString());
        HttpAlertSupport.postJson(mapper, config.getUrl(), body);
    }
}
