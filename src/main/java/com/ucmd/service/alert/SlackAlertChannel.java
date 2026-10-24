package com.ucmd.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucmd.config.AlertConfig;
import com.ucmd.model.entity.AlertEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Slack Incoming Webhook: {"text": "..."} bekliyor. */
@Component
public class SlackAlertChannel implements AlertChannel {

    private final AlertConfig.Webhook config;
    private final ObjectMapper mapper;

    public SlackAlertChannel(AlertConfig alertConfig, ObjectMapper mapper) {
        this.config = alertConfig.getChannels().getSlack();
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "slack";
    }

    @Override
    public boolean isConfigured() {
        return !config.getUrl().isBlank();
    }

    @Override
    public void send(AlertEvent event) throws Exception {
        HttpAlertSupport.postJson(mapper, config.getUrl(), Map.of("text", AlertChannel.format(event)));
    }
}
