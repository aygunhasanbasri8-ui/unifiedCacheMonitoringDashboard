package com.ucmd.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucmd.config.AlertConfig;
import com.ucmd.model.entity.AlertEvent;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class TelegramAlertChannel implements AlertChannel {

    private final AlertConfig.Telegram config;
    private final ObjectMapper mapper;

    public TelegramAlertChannel(AlertConfig alertConfig, ObjectMapper mapper) {
        this.config = alertConfig.getChannels().getTelegram();
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean isConfigured() {
        return !config.getBotToken().isBlank() && !config.getChatId().isBlank();
    }

    @Override
    public void send(AlertEvent event) throws Exception {
        HttpAlertSupport.postJson(mapper,
                "https://api.telegram.org/bot" + config.getBotToken() + "/sendMessage",
                Map.of("chat_id", config.getChatId(), "text", AlertChannel.format(event)));
    }
}
