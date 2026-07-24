package com.nkolay.ucmd.cacheloader.model.dto;

import java.util.List;

/**
 * /v1/cacheloader/keys yanıtı.
 */
public record CacheKeysResponse(int tableCount, int totalKeys, List<TableKeysResult> results) {

    public static CacheKeysResponse from(List<TableKeysResult> results) {
        int total = results.stream().mapToInt(TableKeysResult::count).sum();
        return new CacheKeysResponse(results.size(), total, results);
    }
}
