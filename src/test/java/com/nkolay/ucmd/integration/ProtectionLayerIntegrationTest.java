package com.nkolay.ucmd.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nkolay.ucmd.config.ProtectionConfig;
import com.nkolay.ucmd.exception.InstanceBusyException;
import com.nkolay.ucmd.exception.RateLimitExceededException;
import com.nkolay.ucmd.model.dto.InstanceType;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.CollectionMetrics;
import com.nkolay.ucmd.monitor.ProtectedMonitorExecutor;
import com.nkolay.ucmd.monitor.ProtectedMonitorExecutor.CachedResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class ProtectionLayerIntegrationTest {

    private final InstanceRef instance =
            new InstanceRef(99L, "koruma-test", InstanceType.REDIS, "localhost", 6379, true, false);

    private ProtectionConfig baseConfig() {
        ProtectionConfig c = new ProtectionConfig();
        c.setCheapConcurrency(2);
        c.setExpensiveConcurrency(1);
        c.setCheapTimeoutMs(300);
        c.setExpensiveTimeoutMs(10_000);
        c.setExpensiveRateLimitPerMinute(2);
        c.setBulkheadMaxWaitMs(0);
        c.setCircuitSlidingWindow(1000);   // bu testlerde devre açılmasın
        return c;
    }

    private ProtectedMonitorExecutor build(ProtectionConfig config, long metricsCacheTtlMillis) {
        Cache<String, Object> metricsCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(metricsCacheTtlMillis))
                .maximumSize(100).build();
        Cache<String, Object> lastKnownCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(30))
                .maximumSize(100).build();

        return new ProtectedMonitorExecutor(
                config,
                config.bulkheadRegistry(),
                config.rateLimiterRegistry(),
                config.circuitBreakerRegistry(),
                Executors.newVirtualThreadPerTaskExecutor(),
                metricsCache,
                lastKnownCache,
                new CollectionMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void cacheIsabeti_kaynagaIkinciKezGitmez() {
        ProtectedMonitorExecutor executor = build(baseConfig(), 10_000);
        AtomicInteger calls = new AtomicInteger();

        CachedResult<String> first = executor.executeCachedMetric(
                instance, "health", () -> { calls.incrementAndGet(); return "UP"; });
        CachedResult<String> second = executor.executeCachedMetric(
                instance, "health", () -> { calls.incrementAndGet(); return "UP"; });

        assertThat(first.freshness()).isEqualTo(CachedResult.Freshness.FRESH);
        assertThat(second.freshness()).isEqualTo(CachedResult.Freshness.FRESH);
        assertThat(second.value()).isEqualTo("UP");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void zamanAsimi_oncekiDegerYoksaTimeout() {
        ProtectedMonitorExecutor executor = build(baseConfig(), 10_000);

        CachedResult<String> result = executor.executeCachedMetric(
                instance, "slow", () -> { sleep(1500); return "gec"; });

        assertThat(result.isTimeout()).isTrue();
        assertThat(result.value()).isNull();
    }

    @Test
    void zamanAsimi_oncekiDegerVarsaStale() throws Exception {
        ProtectedMonitorExecutor executor = build(baseConfig(), 50);

        CachedResult<String> ok = executor.executeCachedMetric(instance, "flaky", () -> "v1");
        assertThat(ok.freshness()).isEqualTo(CachedResult.Freshness.FRESH);

        Thread.sleep(150);   // metricsCache düştü, lastKnownCache duruyor

        CachedResult<String> stale = executor.executeCachedMetric(
                instance, "flaky", () -> { sleep(1500); return "v2"; });

        assertThat(stale.isStale()).isTrue();
        assertThat(stale.value()).isEqualTo("v1");
        assertThat(stale.toMeta().isStale()).isTrue();
        assertThat(stale.toMeta().getCollectedAt()).isNotNull();
    }

    @Test
    void hata_oncekiDegerVarsaStaleYoksaUnavailable() throws Exception {
        ProtectedMonitorExecutor executor = build(baseConfig(), 50);

        CachedResult<String> yok = executor.executeCachedMetric(
                instance, "patlak", () -> { throw new IllegalStateException("down"); });
        assertThat(yok.isUnavailable()).isTrue();
        assertThat(yok.value()).isNull();

        executor.executeCachedMetric(instance, "sonra", () -> "v1");
        Thread.sleep(150);

        CachedResult<String> stale = executor.executeCachedMetric(
                instance, "sonra", () -> { throw new IllegalStateException("down"); });
        assertThat(stale.isStale()).isTrue();
        assertThat(stale.value()).isEqualTo("v1");
    }

    @Test
    void pahaliCagri_rateLimitAsiminda429Uretir() {
        ProtectedMonitorExecutor executor = build(baseConfig(), 10_000);

        assertThat(executor.executeExpensive(instance, "scan", () -> "1")).isEqualTo("1");
        assertThat(executor.executeExpensive(instance, "scan", () -> "2")).isEqualTo("2");

        assertThatThrownBy(() -> executor.executeExpensive(instance, "scan", () -> "3"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void pahaliCagri_bulkheadDoluysaInstanceBusy() throws Exception {
        ProtectedMonitorExecutor executor = build(baseConfig(), 10_000);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Thread worker = Thread.ofVirtual().start(() ->
                executor.executeExpensive(instance, "scan", () -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "uzun";
                }));

        assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> executor.executeExpensive(instance, "scan", () -> "kisa"))
                .isInstanceOf(InstanceBusyException.class);

        release.countDown();
        worker.join(5000);
    }

    @Test
    void invalidate_instanceCacheiniTemizler() {
        ProtectedMonitorExecutor executor = build(baseConfig(), 10_000);
        AtomicInteger calls = new AtomicInteger();

        executor.executeCachedMetric(instance, "health",
                () -> { calls.incrementAndGet(); return "UP"; });
        executor.invalidate(instance);
        executor.executeCachedMetric(instance, "health",
                () -> { calls.incrementAndGet(); return "UP"; });

        assertThat(calls.get()).isEqualTo(2);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}