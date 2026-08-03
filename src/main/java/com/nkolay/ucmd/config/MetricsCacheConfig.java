package com.nkolay.ucmd.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Metrik uçları için kısa süreli cache (rehber §2.2).
 * Dashboard her yenilendiğinde hedef sisteme sorgu gitmesini engeller.
 */
@Configuration(proxyBeanMethods = false)
public class MetricsCacheConfig {

    /** Taze veri cache'i — TTL dolunca kayıt düşer. */
    @Bean("metricsCache")
    public Cache<String, Object> metricsCache(
            @Value("${ucmd.cache.ttl-seconds:10}") long ttlSeconds,
            @Value("${ucmd.cache.max-size:500}") long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .build();
    }

    /**
     * Son bilinen değer deposu (STALE fallback — karar 3.5).
     * Hedef erişilemediğinde dashboard boşalmasın diye uzun TTL.
     */
    @Bean("lastKnownCache")
    public Cache<String, Object> lastKnownCache(
            @Value("${ucmd.cache.stale-ttl-minutes:30}") long staleTtlMinutes,
            @Value("${ucmd.cache.max-size:500}") long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(staleTtlMinutes))
                .maximumSize(maxSize)
                .build();
    }
}