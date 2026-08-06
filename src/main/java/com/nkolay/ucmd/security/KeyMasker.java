package com.nkolay.ucmd.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Rehber §9 — key isimlerindeki tanımlayıcıları maskeler.
 * Fintech ortamında key adı hassas veri taşır: "ucmd:merchant:88123:commission"
 * üye işyeri numarasını açığa çıkarır.
 *
 * Kural: 3+ haneli rakam dizileri "***" ile değiştirilir.
 */
@Component
public class KeyMasker {

    private static final Pattern DIGIT_RUN = Pattern.compile("\\d{3,}");
    private static final String MASK = "***";

    private final boolean enabled;

    public KeyMasker(@Value("${ucmd.security.key-masking.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** "ucmd:merchant:88123:commission" -> "ucmd:merchant:***:commission" */
    public String mask(String key) {
        if (key == null) return null;
        if (!enabled) return key;
        return DIGIT_RUN.matcher(key).replaceAll(MASK);
    }

    /** Yapılandırmadan bağımsız HER ZAMAN maskeler — log ve depolama için. */
    public String maskForLog(String key) {
        if (key == null) return null;
        return DIGIT_RUN.matcher(key).replaceAll(MASK);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
