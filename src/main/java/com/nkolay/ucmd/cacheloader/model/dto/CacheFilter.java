package com.nkolay.ucmd.cacheloader.model.dto;

import java.util.Map;

/**
 * Ekrandan gelen serbest filtre alanları (spec). Alan adı -> değer.
 * Örn: {id=15, name=market, status=ACTIVE, tckn=0265, merchantId=3}
 * Hangi alanın kullanılacağına her tablonun accessor'ı karar verir;
 * bir accessor desteklemediği alanı yok sayar.
 */
public record CacheFilter(Map<String, String> fields) {

    public static CacheFilter of(Map<String, String> raw) {
        return new CacheFilter(raw == null ? Map.of() : raw);
    }

    /** Boş/whitespace değerleri null döner. */
    public String get(String key) {
        if (fields == null) return null;
        String v = fields.get(key);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    public boolean isEmpty() {
        if (fields == null || fields.isEmpty()) return true;
        return fields.values().stream().allMatch(v -> v == null || v.trim().isEmpty());
    }
}
