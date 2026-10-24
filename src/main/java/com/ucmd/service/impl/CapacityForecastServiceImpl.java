package com.ucmd.service.impl;

import com.ucmd.enums.MetricStatus;
import com.ucmd.model.dto.CapacityForecast;
import com.ucmd.model.entity.MetricSnapshot;
import com.ucmd.service.CapacityForecastService;
import com.ucmd.service.HistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CapacityForecastServiceImpl implements CapacityForecastService {

    static final int DEFAULT_WINDOW_HOURS = 24;
    /** 30 sn'lik toplamada yaklaşık yarım saat; daha azıyla eğim tamamen gürültü. */
    static final int MIN_POINTS = 60;
    /** Eğim bu kadar küçükse "sabit" sayılıyor; aksi halde 10 yıl sonrası için gün sayısı üretiyordu. */
    private static final double FLAT_FRACTION_PER_DAY = 0.001;
    private static final long CACHE_MILLIS = Duration.ofMinutes(10).toMillis();

    private final HistoryService historyService;
    private final int maxWindowHours;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    public CapacityForecastServiceImpl(HistoryService historyService,
                                       @Value("${ucmd.snapshot.retention-days:7}") int retentionDays) {
        this.historyService = historyService;
        this.maxWindowHours = Math.max(1, retentionDays) * 24;
    }

    @Override
    public CapacityForecast forecast(Long instanceId, Integer windowHours) {
        int hours = windowHours == null || windowHours <= 0 ? DEFAULT_WINDOW_HOURS : Math.min(windowHours, maxWindowHours);
        List<MetricSnapshot> snapshots = historyService.getSnapshots(instanceId, hours * 60).stream()
                .filter(s -> "UP".equalsIgnoreCase(s.getStatus()) && s.getCollectedAt() != null)
                .toList();
        CapacityForecast result = compute(instanceId, snapshots, hours);
        cache.put(instanceId, new Cached(result.daysToFull(), System.currentTimeMillis()));
        return result;
    }

    @Override
    public Double cachedDaysToFull(Long instanceId) {
        Cached cached = cache.get(instanceId);
        if (cached != null && System.currentTimeMillis() - cached.at < CACHE_MILLIS) {
            return cached.daysToFull;
        }
        return forecast(instanceId, null).daysToFull();
    }

    static CapacityForecast compute(Long instanceId, List<MetricSnapshot> snapshots, int hours) {
        if (snapshots.isEmpty()) {
            return new CapacityForecast(instanceId, MetricStatus.NOT_READY, "UNKNOWN", 0, 0,
                    null, null, null, hours, 0);
        }
        MetricSnapshot last = snapshots.get(snapshots.size() - 1);
        long max = last.getMemoryMax();
        if (max <= 0) {
            // Redis'te maxmemory=0 sınırsız demek; dolma tarihi tanımsız
            return new CapacityForecast(instanceId, MetricStatus.NOT_APPLICABLE, "UNKNOWN", last.getMemoryUsed(), 0,
                    null, null, null, hours, snapshots.size());
        }
        if (snapshots.size() < MIN_POINTS) {
            return new CapacityForecast(instanceId, MetricStatus.NOT_READY, "UNKNOWN", last.getMemoryUsed(), max,
                    null, null, null, hours, snapshots.size());
        }

        // En küçük kareler: x = gün (ilk noktadan itibaren), y = kullanılan bayt
        long origin = epochMillis(snapshots.get(0));
        double n = snapshots.size(), sx = 0, sy = 0, sxx = 0, sxy = 0, syy = 0;
        for (MetricSnapshot s : snapshots) {
            double x = (epochMillis(s) - origin) / 86_400_000.0;
            double y = s.getMemoryUsed();
            sx += x; sy += y; sxx += x * x; sxy += x * y; syy += y * y;
        }
        double varX = n * sxx - sx * sx;
        if (varX <= 0) {
            return new CapacityForecast(instanceId, MetricStatus.NOT_READY, "UNKNOWN", last.getMemoryUsed(), max,
                    null, null, null, hours, snapshots.size());
        }
        double slope = (n * sxy - sx * sy) / varX;
        double varY = n * syy - sy * sy;
        double r2 = varY <= 0 ? 1.0 : Math.pow(n * sxy - sx * sy, 2) / (varX * varY);

        String trend;
        Double daysToFull = null;
        if (Math.abs(slope) < FLAT_FRACTION_PER_DAY * max) {
            trend = "STABLE";
        } else if (slope < 0) {
            trend = "DECREASING";
        } else {
            trend = "GROWING";
            daysToFull = Math.max(0.0, (max - last.getMemoryUsed()) / slope);
        }
        return new CapacityForecast(instanceId, MetricStatus.AVAILABLE, trend, last.getMemoryUsed(), max,
                round(slope), daysToFull == null ? null : round(daysToFull), round(r2), hours, snapshots.size());
    }

    private static long epochMillis(MetricSnapshot s) {
        return s.getCollectedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record Cached(Double daysToFull, long at) {
    }
}
