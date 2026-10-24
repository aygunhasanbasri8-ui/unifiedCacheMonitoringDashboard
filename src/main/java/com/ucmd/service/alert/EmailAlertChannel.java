package com.ucmd.service.alert;

import com.ucmd.config.AlertConfig;
import com.ucmd.model.entity.AlertEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** spring.mail.host tanımlı değilse JavaMailSender oluşmuyor, kanal kapalı kalıyor. */
@Component
public class EmailAlertChannel implements AlertChannel {

    private final AlertConfig.Email config;
    private final ObjectProvider<JavaMailSender> mailSender;

    public EmailAlertChannel(AlertConfig alertConfig, ObjectProvider<JavaMailSender> mailSender) {
        this.config = alertConfig.getChannels().getEmail();
        this.mailSender = mailSender;
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean isConfigured() {
        return !config.getTo().isEmpty() && mailSender.getIfAvailable() != null;
    }

    @Override
    public void send(AlertEvent event) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(config.getFrom());
        mail.setTo(config.getTo().toArray(String[]::new));
        mail.setSubject("[UCMD] " + event.getState() + " " + event.getRuleName() + " - " + event.getInstanceName());
        mail.setText(AlertChannel.format(event));
        mailSender.getObject().send(mail);
    }
}
