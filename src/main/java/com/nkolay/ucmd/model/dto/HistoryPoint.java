package com.nkolay.ucmd.model.dto;

import java.time.Instant;

/** Grafik için tek zaman noktası. */
public record HistoryPoint(
        Instant timestamp,
        String status,          // UP | DOWN
        Double cpuPercent,
        Long memoryUsed,
        Long keyCount,
        Integer clientCount,
        Double hitRatio,
        Long opsPerSec,
        Long evictedKeys
) {}
