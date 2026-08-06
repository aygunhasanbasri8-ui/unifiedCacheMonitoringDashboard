package com.nkolay.ucmd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** Rehber §9 — key erişim güvenliği: prefix allowlist ve maskeleme. */
@Configuration
@ConfigurationProperties(prefix = "ucmd.security")
public class KeySecurityConfig {

    private List<String> allowedPrefixes = new ArrayList<>();
    private KeyMaskingProps keyMasking = new KeyMaskingProps();

    public List<String> getAllowedPrefixes() { return allowedPrefixes; }
    public void setAllowedPrefixes(List<String> allowedPrefixes) {
        this.allowedPrefixes = allowedPrefixes;
    }

    public KeyMaskingProps getKeyMasking() { return keyMasking; }
    public void setKeyMasking(KeyMaskingProps keyMasking) { this.keyMasking = keyMasking; }

    public static class KeyMaskingProps {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /** Liste boşsa kısıt yok. Doluysa prefix listede olmalı. */
    public boolean isPrefixAllowed(String prefix) {
        if (allowedPrefixes == null || allowedPrefixes.isEmpty()) return true;
        if (prefix == null) return false;
        return allowedPrefixes.stream().anyMatch(prefix::startsWith);
    }
}
