package com.nkolay.ucmd.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.nkolay.ucmd.config.ProtectionConfig;
import com.nkolay.ucmd.exception.CircuitOpenException;
import com.nkolay.ucmd.exception.InstanceBusyException;
import com.nkolay.ucmd.exception.MonitorTimeoutException;
import com.nkolay.ucmd.exception.RateLimitExceededException;
import com.nkolay.ucmd.model.dto.MetricMeta;
import com.nkolay.ucmd.model.entity.InstanceRef;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

// Sıra: cache → rate limiter → bulkhead → circuit breaker → timeout → çağrı
// Cache en dışta: hit olduğunda hiçbir limit tüketilmez
@Component
public class ProtectedMonitorExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ProtectedMonitorExecutor.class);

    private final ProtectionConfig config;
    private final BulkheadRegistry bulkheadRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ExecutorService virtualThreadExecutor;
    private final Cache<String, Object> metricsCache;
    private final Cache<String, Object> lastKnownCache;
    private final CollectionMetrics collectionMetrics;

    public ProtectedMonitorExecutor(ProtectionConfig config,
                                    BulkheadRegistry bulkheadRegistry,
                                    RateLimiterRegistry rateLimiterRegistry,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    ExecutorService virtualThreadExecutor,
                                    @Qualifier("metricsCache") Cache<String, Object> metricsCache,
                                    @Qualifier("lastKnownCache") Cache<String, Object> lastKnownCache,
                                    CollectionMetrics collectionMetrics) {
        this.config = config;
        this.bulkheadRegistry = bulkheadRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.metricsCache = metricsCache;
        this.lastKnownCache = lastKnownCache;
        this.collectionMetrics = collectionMetrics;
    }

    public <T> CachedResult<T> executeCachedMetric(InstanceRef instance, String operation, Supplier<T> call) {
        return executeCachedMetric(instance, operation, operation, call);
    }

    @SuppressWarnings("unchecked")
    public <T> CachedResult<T> executeCachedMetric(InstanceRef instance,
                                                   String cacheSuffix,
                                                   String logLabel,
                                                   Supplier<T> call) {
        String key = cacheKey(instance, cacheSuffix);

        Timestamped<T> cached = (Timestamped<T>) metricsCache.getIfPresent(key);
        if (cached != null) {
            return CachedResult.fresh(cached.value(), cached.collectedAt());
        }

        try {
            T result = runProtected(instance, logLabel, call,
                    "cheap", config.getCheapTimeoutMs(), false);
            Timestamped<T> stamped = new Timestamped<>(result, Instant.now());
            metricsCache.put(key, stamped);
            lastKnownCache.put(key, stamped);
            return CachedResult.fresh(result, stamped.collectedAt());

        } catch (MonitorTimeoutException e) {
            logger.warn("Metric call '{}' timed out for {}", logLabel, instance.getName());
            Timestamped<T> lastKnown = (Timestamped<T>) lastKnownCache.getIfPresent(key);
            if (lastKnown != null) {
                logger.debug("Serving STALE value for {} / {}", instance.getName(), logLabel);
                return CachedResult.stale(lastKnown.value(), lastKnown.collectedAt());
            }
            return CachedResult.timeout();

        } catch (Exception e) {
            logger.warn("Metric call '{}' failed for {}: {}",
                    logLabel, instance.getName(), e.getMessage());

            Timestamped<T> lastKnown = (Timestamped<T>) lastKnownCache.getIfPresent(key);
            if (lastKnown != null) {
                logger.debug("Serving STALE value for {} / {}", instance.getName(), logLabel);
                return CachedResult.stale(lastKnown.value(), lastKnown.collectedAt());
            }
            return CachedResult.unavailable();
        }
    }

    // Cache yok — SCAN tabanlı; parametre uzayı her çağrıda farklı olabilir
    public <T> T executeExpensive(InstanceRef instance, String operation, Supplier<T> call) {
        collectionMetrics.scanStarted(instance.getName());
        try {
            return runProtected(instance, operation, call,
                    "default", config.getExpensiveTimeoutMs(), true);
        } finally {
            collectionMetrics.scanFinished(instance.getName());
        }
    }

    private <T> T runProtected(InstanceRef instance,
                               String operation,
                               Supplier<T> call,
                               String bulkheadConfigName,
                               long timeoutMs,
                               boolean enforceLimits) {

        String id = instanceKey(instance);

        // 1) Rate limiter — yalnızca pahalı işlemlerde
        if (enforceLimits) {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("expensive-" + id);
            if (!rateLimiter.acquirePermission()) {
                collectionMetrics.recordRateLimitRejection(instance.getName());
                throw new RateLimitExceededException(
                        "instance '" + instance.getName() + "', işlem '" + operation + "'");
            }
        }

        // 2) Bulkhead
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(
                bulkheadConfigName + "-" + id, bulkheadConfigName);

        if (!bulkhead.tryAcquirePermission()) {
            collectionMetrics.recordBulkheadRejection(instance.getName());
            if (enforceLimits) {
                throw new InstanceBusyException(
                        "instance '" + instance.getName() + "' eş zamanlılık limitinde");
            }
            throw new InstanceBusyException("instance '" + instance.getName() + "' meşgul");
        }

        try {
            // 3) Circuit breaker
            CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("monitor-" + id);
            try {
                breaker.acquirePermission();
            } catch (CallNotPermittedException e) {
                collectionMetrics.recordCircuitOpen(instance.getName());
                throw new CircuitOpenException(
                        "instance '" + instance.getName() + "' geçici olarak devre dışı");
            }

            long start = System.nanoTime();
            try {
                T result = withTimeout(call, timeoutMs, instance, operation);
                breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return result;
            } catch (Exception e) {
                breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, e);
                throw e;
            }

        } catch (BulkheadFullException e) {
            throw new InstanceBusyException("instance '" + instance.getName() + "' meşgul");
        } finally {
            bulkhead.releasePermission();
        }
    }

    private <T> T withTimeout(Supplier<T> call, long timeoutMs,
                              InstanceRef instance, String operation) {
        Future<T> future = virtualThreadExecutor.submit(call::get);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            future.cancel(true);
            collectionMetrics.recordTimeout(instance.getName(), operation);
            throw new MonitorTimeoutException(
                    "instance '" + instance.getName() + "', işlem '" + operation
                            + "' (" + timeoutMs + " ms)");

        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new MonitorTimeoutException("çağrı kesildi: " + operation);

        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }

    private String instanceKey(InstanceRef instance) {
        return String.valueOf(instance.getId());
    }

    private String cacheKey(InstanceRef instance, String operation) {
        return instance.getId() + ":" + operation;
    }

    public void invalidate(InstanceRef instance) {
        String prefix = instance.getId() + ":";
        metricsCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        lastKnownCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    public record Timestamped<T>(T value, Instant collectedAt) {}

    public record CachedResult<T>(T value, Freshness freshness, Instant collectedAt) {
        public enum Freshness { FRESH, STALE, UNAVAILABLE, TIMEOUT }

        public static <T> CachedResult<T> fresh(T v, Instant at) {
            return new CachedResult<>(v, Freshness.FRESH, at);
        }
        public static <T> CachedResult<T> stale(T v, Instant at) {
            return new CachedResult<>(v, Freshness.STALE, at);
        }
        public static <T> CachedResult<T> unavailable() {
            return new CachedResult<>(null, Freshness.UNAVAILABLE, null);
        }
        public static <T> CachedResult<T> timeout() {
            return new CachedResult<>(null, Freshness.TIMEOUT, null);
        }
        public boolean isStale() { return freshness == Freshness.STALE; }
        public boolean isUnavailable() { return freshness == Freshness.UNAVAILABLE; }
        public boolean isTimeout() { return freshness == Freshness.TIMEOUT; }

        public MetricMeta toMeta() {
            return new MetricMeta(collectedAt, isStale());
        }
    }
}