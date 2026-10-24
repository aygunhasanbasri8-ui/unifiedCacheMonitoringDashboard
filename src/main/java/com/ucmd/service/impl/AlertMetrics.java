package com.ucmd.service.impl;

import com.ucmd.config.AlertConfig;
import com.ucmd.enums.AlertMetric;
import com.ucmd.model.entity.MetricSnapshot;

import java.time.Duration;

/** Snapshot'tan kuralın değerini çıkarır. null = bu tur karar verilemez, durum değişmez. */
final class AlertMetrics {

    private AlertMetrics() {
    }

    static AlertServiceImpl.Reading read(AlertConfig.Rule rule, MetricSnapshot snapshot, MetricSnapshot previous) {
        boolean down = "DOWN".equalsIgnoreCase(snapshot.getStatus());
        if (rule.getMetric() == AlertMetric.INSTANCE_DOWN) {
            return new AlertServiceImpl.Reading(null, down);
        }
        // Erişilemeyen instance'ın sıfırlanmış alanlarıyla "bellek %0, düzeldi" demeyelim
        if (down) {
            return null;
        }
        Double value = switch (rule.getMetric()) {
            case MEMORY_PERCENT -> snapshot.getMemoryMax() > 0
                    ? snapshot.getMemoryUsed() * 100.0 / snapshot.getMemoryMax() : null;
            case CPU_PERCENT -> snapshot.getCpuPercent();
            case HIT_RATIO_PERCENT -> snapshot.getHitRatio() != null ? snapshot.getHitRatio() * 100.0 : null;
            case FRAGMENTATION_RATIO -> snapshot.getFragmentationRatio() > 0 ? snapshot.getFragmentationRatio() : null;
            case CLIENT_COUNT -> (double) snapshot.getClientCount();
            case CONNECTED_REPLICAS -> "master".equalsIgnoreCase(snapshot.getReplicationRole())
                    ? (double) snapshot.getConnectedReplicas() : null;
            case EVICTIONS_PER_MINUTE -> evictionsPerMinute(snapshot, previous);
            case INSTANCE_DOWN -> null;
        };
        if (value == null) {
            return null;
        }
        return new AlertServiceImpl.Reading(value, rule.getOperator().breached(value, rule.getThreshold()));
    }

    private static Double evictionsPerMinute(MetricSnapshot current, MetricSnapshot previous) {
        if (previous == null || previous.getCollectedAt() == null || current.getCollectedAt() == null) {
            return null;
        }
        long delta = current.getEvictedKeys() - previous.getEvictedKeys();
        double minutes = Duration.between(previous.getCollectedAt(), current.getCollectedAt()).toMillis() / 60000.0;
        // Sayaç geriye gittiyse instance yeniden başlamış; fark anlamsız
        if (delta < 0 || minutes <= 0) {
            return null;
        }
        return delta / minutes;
    }
}
