package com.nkolay.ucmd.cacheloader.model.dto;

import java.util.List;

/**
 * Bir tablo için seed sonucu: yazılan/karşılanan key listesi.
 */
public record TableKeysResult(String table, int count, List<String> keys) {
}
