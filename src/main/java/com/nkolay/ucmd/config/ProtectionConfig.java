package com.nkolay.ucmd.config;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * İzlenen cache sistemlerini koruma katmanı (rehber §2.2, §2.3).
 * Limitler instance bazlıdır — registry'den dinamik isimle alınır.
 */
@Configuration
@ConfigurationProperties(prefix = "ucmd.protection")
public class ProtectionConfig {

    // ── Yapılandırılabilir alanlar ──
    private int cheapConcurrency = 5;
    private int expensiveConcurrency = 2;
    private long cheapTimeoutMs = 5000;
    private long expensiveTimeoutMs = 15000;
    private int expensiveRateLimitPerMinute = 10;
    private int circuitFailureRatePercent = 50;
    private int circuitSlidingWindow = 10;
    private long circuitOpenDurationSeconds = 30;
    private long bulkheadMaxWaitMs = 0;

    // ── Registry bean'leri ──

    @Bean
    public BulkheadRegistry bulkheadRegistry() {
        // Varsayılan: pahalı işlemler. Ucuz olanlar ayrı config ile eklenir.
        BulkheadConfig expensive = BulkheadConfig.custom()
                .maxConcurrentCalls(expensiveConcurrency)
                .maxWaitDuration(Duration.ofMillis(bulkheadMaxWaitMs))
                .build();

        BulkheadRegistry registry = BulkheadRegistry.of(expensive);

        registry.addConfiguration("cheap", BulkheadConfig.custom()
                .maxConcurrentCalls(cheapConcurrency)
                .maxWaitDuration(Duration.ofMillis(bulkheadMaxWaitMs))
                .build());

        return registry;
    }

    @Bean
    public RateLimiterRegistry rateLimiterRegistry() {
        RateLimiterConfig expensive = RateLimiterConfig.custom()
                .limitForPeriod(expensiveRateLimitPerMinute)
                .limitRefreshPeriod(Duration.ofMinutes(1))
                .timeoutDuration(Duration.ZERO)   // beklemeden reddet
                .build();
        return RateLimiterRegistry.of(expensive);
    }

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(circuitSlidingWindow)
                .minimumNumberOfCalls(circuitSlidingWindow)
                .failureRateThreshold(circuitFailureRatePercent)
                .waitDurationInOpenState(Duration.ofSeconds(circuitOpenDurationSeconds))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    // ── Getter / Setter ──

    public int getCheapConcurrency() { return cheapConcurrency; }
    public void setCheapConcurrency(int v) { this.cheapConcurrency = v; }

    public int getExpensiveConcurrency() { return expensiveConcurrency; }
    public void setExpensiveConcurrency(int v) { this.expensiveConcurrency = v; }

    public long getCheapTimeoutMs() { return cheapTimeoutMs; }
    public void setCheapTimeoutMs(long v) { this.cheapTimeoutMs = v; }

    public long getExpensiveTimeoutMs() { return expensiveTimeoutMs; }
    public void setExpensiveTimeoutMs(long v) { this.expensiveTimeoutMs = v; }

    public int getExpensiveRateLimitPerMinute() { return expensiveRateLimitPerMinute; }
    public void setExpensiveRateLimitPerMinute(int v) { this.expensiveRateLimitPerMinute = v; }

    public int getCircuitFailureRatePercent() { return circuitFailureRatePercent; }
    public void setCircuitFailureRatePercent(int v) { this.circuitFailureRatePercent = v; }

    public int getCircuitSlidingWindow() { return circuitSlidingWindow; }
    public void setCircuitSlidingWindow(int v) { this.circuitSlidingWindow = v; }

    public long getCircuitOpenDurationSeconds() { return circuitOpenDurationSeconds; }
    public void setCircuitOpenDurationSeconds(long v) { this.circuitOpenDurationSeconds = v; }

    public long getBulkheadMaxWaitMs() { return bulkheadMaxWaitMs; }
    public void setBulkheadMaxWaitMs(long v) { this.bulkheadMaxWaitMs = v; }
}