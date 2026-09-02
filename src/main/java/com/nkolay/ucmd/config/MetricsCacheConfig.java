package com.nkolay.ucmd.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class MetricsCacheConfig {

    @Bean("metricsCache")
    public Cache<String, Object> metricsCache(
            @Value("${ucmd.cache.ttl-seconds:10}") long ttlSeconds,
            @Value("${ucmd.cache.max-size:500}") long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(maxSize)
                .build();
    }

    // Hedef erişilemediğinde boş dashboard yerine son bilinen değer döner
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