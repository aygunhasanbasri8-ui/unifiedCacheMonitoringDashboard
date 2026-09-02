package com.nkolay.ucmd.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// Gauge'lar için AtomicLong referansı tutulur; her çağrıda yeniden kayıt yapılmaz
@Component
public class CollectionMetrics {

    private final MeterRegistry registry;

    // instance -> meter (tekrar kayıt yapmamak için önbellek)
    private final Map<String, Timer> durationTimers = new ConcurrentHashMap<>();
    private final Map<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> timeoutCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> rateLimitCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> bulkheadCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> circuitOpenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> keysScannedCounters = new ConcurrentHashMap<>();

    // Gauge'ların arkasındaki canlı referanslar
    private final Map<String, AtomicLong> lastSuccessEpoch = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> activeScans = new ConcurrentHashMap<>();

    public CollectionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }


    public void recordCollectionSuccess(String instanceName, long durationMillis) {
        durationTimers.computeIfAbsent(instanceName, n -> Timer.builder("ucmd.collection.duration")
                        .description("Instance metrik toplama süresi")
                        .tag("instance", n)
                        .register(registry))
                .record(durationMillis, TimeUnit.MILLISECONDS);

        successCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.collection.success")
                        .description("Başarılı toplama sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment();

        lastSuccessGauge(instanceName).set(System.currentTimeMillis() / 1000);
    }

    public void recordCollectionFailure(String instanceName) {
        failureCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.collection.failure")
                        .description("Başarısız toplama sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment();

        // Gauge'ların kayıtlı olduğundan emin ol (hiç başarı olmasa bile görünsün)
        lastSuccessGauge(instanceName);
    }


    public void recordTimeout(String instanceName, String operation) {
        timeoutCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.protection.timeout")
                        .description("Zaman aşımına uğrayan çağrı sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment();
    }

    public void recordRateLimitRejection(String instanceName) {
        rateLimitCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.protection.ratelimit.rejected")
                        .description("Rate limit ile reddedilen çağrı sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment();
    }

    public void recordBulkheadRejection(String instanceName) {
        bulkheadCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.protection.bulkhead.rejected")
                        .description("Bulkhead ile reddedilen çağrı sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment();
    }

    public void recordCircuitOpen(String instanceName) {
        circuitOpenCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.protection.circuit.open")
                        .description("Circuit breaker açıkken reddedilen çağrı sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment();
    }


    public void recordKeysScanned(String instanceName, long count) {
        keysScannedCounters.computeIfAbsent(instanceName, n -> Counter.builder("ucmd.scan.keys.scanned")
                        .description("Taranan key sayısı")
                        .tag("instance", n)
                        .register(registry))
                .increment(count);
    }

    public void scanStarted(String instanceName) {
        activeScanGauge(instanceName).incrementAndGet();
    }

    public void scanFinished(String instanceName) {
        AtomicLong active = activeScanGauge(instanceName);
        if (active.decrementAndGet() < 0) {
            active.set(0);
        }
    }


    public long lastSuccessEpochSeconds(String instanceName) {
        AtomicLong value = lastSuccessEpoch.get(instanceName);
        return value != null ? value.get() : 0;
    }

    public long staleAgeSeconds(String instanceName) {
        long last = lastSuccessEpochSeconds(instanceName);
        if (last <= 0) return -1;
        return Math.max(0, (System.currentTimeMillis() / 1000) - last);
    }


    private AtomicLong lastSuccessGauge(String instanceName) {
        return lastSuccessEpoch.computeIfAbsent(instanceName, n -> {
            AtomicLong holder = new AtomicLong(0);
            registry.gauge("ucmd.collection.last.success",
                    java.util.List.of(io.micrometer.core.instrument.Tag.of("instance", n)),
                    holder, AtomicLong::doubleValue);
            // Son başarılıdan geçen süre — aynı holder'dan türetilir
            registry.gauge("ucmd.collection.stale.age",
                    java.util.List.of(io.micrometer.core.instrument.Tag.of("instance", n)),
                    holder, h -> {
                        long last = h.get();
                        if (last <= 0) return -1d;
                        return Math.max(0, (System.currentTimeMillis() / 1000) - last);
                    });
            return holder;
        });
    }

    private AtomicLong activeScanGauge(String instanceName) {
        return activeScans.computeIfAbsent(instanceName, n -> {
            AtomicLong holder = new AtomicLong(0);
            registry.gauge("ucmd.scan.active",
                    java.util.List.of(io.micrometer.core.instrument.Tag.of("instance", n)),
                    holder, AtomicLong::doubleValue);
            return holder;
        });
    }
}
