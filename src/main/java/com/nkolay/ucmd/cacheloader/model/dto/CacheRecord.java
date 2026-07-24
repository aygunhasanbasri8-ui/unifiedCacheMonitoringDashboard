package com.nkolay.ucmd.cacheloader.model.dto;

/**
 * Bir tablo satırının cache'e yazılmadan önceki hali.
 * id -> key üretiminde, payload -> value (JSON) üretiminde kullanılır.
 */
public record CacheRecord(String id, Object payload) {
}
