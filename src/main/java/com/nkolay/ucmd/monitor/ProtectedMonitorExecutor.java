package com.nkolay.ucmd.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.nkolay.ucmd.config.ProtectionConfig;
import com.nkolay.ucmd.exception.CircuitOpenException;
import com.nkolay.ucmd.exception.InstanceBusyException;
import com.nkolay.ucmd.exception.MonitorTimeoutException;
import com.nkolay.ucmd.exception.RateLimitExceededException;
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Monitor çağrılarını koruma katmanından geçirir (rehber §2.2, §2.3).
 *
 * Sıra: cache → rate limiter → bulkhead → circuit breaker → timeout → çağrı
 * Cache en dışta: hit durumunda hiçbir limit tüketilmez.
 */
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

    public ProtectedMonitorExecutor(ProtectionConfig config,
                                    BulkheadRegistry bulkheadRegistry,
                                    RateLimiterRegistry rateLimiterRegistry,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    ExecutorService virtualThreadExecutor,
                                    @Qualifier("metricsCache") Cache<String, Object> metricsCache,
                                    @Qualifier("lastKnownCache") Cache<String, Object> lastKnownCache) {
        this.config = config;
        this.bulkheadRegistry = bulkheadRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.metricsCache = metricsCache;
        this.lastKnownCache = lastKnownCache;
    }

    // ═══════════════ Genel API ═══════════════

    /**
     * UCUZ + CACHE'Lİ okuma (metrik uçları).
     * Karar 2.8: rate limit ve bulkhead reddi YOK — dashboard kilitlenmesin.
     * Karar 3.5: hata durumunda son bilinen değer STALE olarak döner.
     *
     * @param staleMarker cache'ten dönen değeri STALE işaretleyen fonksiyon (null olabilir)
     */
    @SuppressWarnings("unchecked")
    public <T> CachedResult<T> executeCachedMetric(InstanceRef instance,
                                                   String operation,
                                                   Supplier<T> call) {
        String key = cacheKey(instance, operation);

        T cached = (T) metricsCache.getIfPresent(key);
        if (cached != null) {
            return CachedResult.fresh(cached);
        }

        try {
            T result = runProtected(instance, operation, call,
                    "cheap", config.getCheapTimeoutMs(), false);
            metricsCache.put(key, result);
            lastKnownCache.put(key, result);
            return CachedResult.fresh(result);

        } catch (Exception e) {
            logger.warn("Metric call '{}' failed for {}: {}",
                    operation, instance.getName(), e.getMessage());

            T lastKnown = (T) lastKnownCache.getIfPresent(key);
            if (lastKnown != null) {
                logger.debug("Serving STALE value for {} / {}", instance.getName(), operation);
                return CachedResult.stale(lastKnown);
            }
            return CachedResult.unavailable();
        }
    }

    /**
     * PAHALI okuma (SCAN tabanlı: key tarama, top-N, pattern istatistikleri).
     * Tüm korumalar aktif; reddedilirse exception fırlar → 429 / 503 / 504.
     * Cache YOK (karar 3.3 — parametre uzayı çok geniş).
     */
    public <T> T executeExpensive(InstanceRef instance, String operation, Supplier<T> call) {
        return runProtected(instance, operation, call,
                "default", config.getExpensiveTimeoutMs(), true);
    }

    // ═══════════════ Çekirdek ═══════════════

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
                throw new RateLimitExceededException(
                        "instance '" + instance.getName() + "', işlem '" + operation + "'");
            }
        }

        // 2) Bulkhead — instance bazlı eş zamanlılık limiti
        Bulkhead bulkhead = bulkheadRegistry.bulkhead(
                bulkheadConfigName + "-" + id, bulkheadConfigName);

        if (!bulkhead.tryAcquirePermission()) {
            if (enforceLimits) {
                throw new InstanceBusyException(
                        "instance '" + instance.getName() + "' eş zamanlılık limitinde");
            }
            // Ucuz işlemlerde bulkhead dolu → korumasız devam etme, hata say
            throw new InstanceBusyException("instance '" + instance.getName() + "' meşgul");
        }

        try {
            // 3) Circuit breaker
            CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("monitor-" + id);
            try {
                breaker.acquirePermission();
            } catch (CallNotPermittedException e) {
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

    /** Çağrıyı virtual thread'e verip timeout uygular. */
    private <T> T withTimeout(Supplier<T> call, long timeoutMs,
                              InstanceRef instance, String operation) {
        Future<T> future = virtualThreadExecutor.submit(call::get);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            future.cancel(true);
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

    // ═══════════════ Yardımcılar ═══════════════

    private String instanceKey(InstanceRef instance) {
        return String.valueOf(instance.getId());
    }

    private String cacheKey(InstanceRef instance, String operation) {
        return instance.getId() + ":" + operation;
    }

    /** Belirli bir instance'ın cache'ini temizler (yapılandırma değişince). */
    public void invalidate(InstanceRef instance) {
        String prefix = instance.getId() + ":";
        metricsCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
        lastKnownCache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ═══════════════ Sonuç sarmalayıcı ═══════════════

    /** Değerin taze mi, bayat mı, yok mu olduğunu taşır. */
    public record CachedResult<T>(T value, Freshness freshness) {
        public enum Freshness { FRESH, STALE, UNAVAILABLE }

        public static <T> CachedResult<T> fresh(T v) {
            return new CachedResult<>(v, Freshness.FRESH);
        }
        public static <T> CachedResult<T> stale(T v) {
            return new CachedResult<>(v, Freshness.STALE);
        }
        public static <T> CachedResult<T> unavailable() {
            return new CachedResult<>(null, Freshness.UNAVAILABLE);
        }
        public boolean isStale() { return freshness == Freshness.STALE; }
        public boolean isUnavailable() { return freshness == Freshness.UNAVAILABLE; }
    }
}