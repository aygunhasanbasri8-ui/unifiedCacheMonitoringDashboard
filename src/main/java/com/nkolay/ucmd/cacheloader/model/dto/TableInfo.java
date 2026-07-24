package com.nkolay.ucmd.cacheloader.model.dto;

import java.util.List;

/**
 * Ekranın tablo seçimini config'den beslemesi için tablo meta bilgisi.
 */
public record TableInfo(String table, String keyPrefix, long ttlSeconds, List<String> filterFields) {
}
