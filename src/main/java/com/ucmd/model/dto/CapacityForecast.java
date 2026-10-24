package com.ucmd.model.dto;

import com.ucmd.enums.MetricStatus;

/**
 * Bellek eğiliminden "bu hızla maxmemory'ye ne zaman ulaşılır" tahmini.
 *
 * @param status          AVAILABLE; yeterli veri yoksa NOT_READY, bellek sınırı bilinmiyorsa NOT_APPLICABLE
 * @param growthBytesPerDay doğrusal eğilimin eğimi (negatif = azalıyor)
 * @param daysToFull      büyüme yoksa ya da sınır bilinmiyorsa null
 * @param rSquared        eğilimin veriye uyumu (0-1); düşükse tahmin gürültülü
 */
public record CapacityForecast(Long instanceId, MetricStatus status, String trend, long memoryUsed, long memoryMax,
                               Double growthBytesPerDay, Double daysToFull, Double rSquared,
                               int windowHours, int pointCount) {
}
