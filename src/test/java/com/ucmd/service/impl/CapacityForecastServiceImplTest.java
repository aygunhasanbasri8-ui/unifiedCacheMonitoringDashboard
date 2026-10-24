package com.ucmd.service.impl;

import com.ucmd.enums.MetricStatus;
import com.ucmd.model.dto.CapacityForecast;
import com.ucmd.model.entity.MetricSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToLongFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Kapasite tahmini")
class CapacityForecastServiceImplTest {

    private static final long GB = 1024L * 1024 * 1024;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 24, 0, 0);

    @Test
    @DisplayName("günde 100 MB büyüyen 1 GB'lık instance'ın kalan günü doğru çıkıyor")
    void linearGrowth() {
        // 24 saat, 30 sn'de bir; 400 MB'tan başlayıp günde 100 MB artıyor (küçük dalgalanmayla)
        List<MetricSnapshot> series = series(2880, GB, i -> 400L * 1024 * 1024 + i * (100L * 1024 * 1024 / 2880)
                + (i % 2 == 0 ? 3_000_000 : -3_000_000));

        CapacityForecast f = CapacityForecastServiceImpl.compute(1L, series, 24);

        assertThat(f.status()).isEqualTo(MetricStatus.AVAILABLE);
        assertThat(f.trend()).isEqualTo("GROWING");
        assertThat(f.growthBytesPerDay()).isCloseTo(100.0 * 1024 * 1024, within(2.0 * 1024 * 1024));
        // son nokta ~500 MB, kalan ~524 MB / 100 MB = ~5.2 gün
        assertThat(f.daysToFull()).isCloseTo(5.24, within(0.1));
        assertThat(f.rSquared()).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("sabit bellek dolma tarihi üretmiyor")
    void flatUsage() {
        CapacityForecast f = CapacityForecastServiceImpl.compute(1L,
                series(500, GB, i -> 300L * 1024 * 1024 + (i % 3) * 1000), 24);
        assertThat(f.trend()).isEqualTo("STABLE");
        assertThat(f.daysToFull()).isNull();
    }

    @Test
    @DisplayName("az veri ve tanımsız sınır karar üretmiyor")
    void notEnoughDataOrUnlimited() {
        assertThat(CapacityForecastServiceImpl.compute(1L, series(10, GB, i -> i * 1000L), 24).status())
                .isEqualTo(MetricStatus.NOT_READY);
        assertThat(CapacityForecastServiceImpl.compute(1L, series(500, 0, i -> i * 1000L), 24).status())
                .isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(CapacityForecastServiceImpl.compute(1L, List.of(), 24).status())
                .isEqualTo(MetricStatus.NOT_READY);
    }

    private static List<MetricSnapshot> series(int count, long max, IntToLongFunction used) {
        List<MetricSnapshot> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            MetricSnapshot s = new MetricSnapshot();
            s.setInstanceId(1L);
            s.setStatus("UP");
            s.setCollectedAt(START.plusSeconds(30L * i));
            s.setMemoryMax(max);
            s.setMemoryUsed(used.applyAsLong(i));
            list.add(s);
        }
        return list;
    }
}
