package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class RedisMonitor implements CacheMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RedisMonitor.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    public RedisMonitor(RedisTemplate<String, String> redisTemplate,
                        RedisConnectionFactory connectionFactory) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public String getSupportedType() {
        return "REDIS";
    }

    @Override
    public boolean isReachable(InstanceRef instance) {
        try {
            // execute() ile connection pool'dan güvenli al
            String result = redisTemplate.execute((RedisCallback<String>) conn -> {
                byte[] pong = conn.ping().getBytes();
                return pong != null ? new String(pong) : null;
            });
            return "PONG".equalsIgnoreCase(result);
        } catch (Exception e) {
            logger.warn("Redis reachability check failed for {}: {}",
                    instance.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public HealthInfo getHealth(InstanceRef instance) {
        try {
            return redisTemplate.execute((RedisCallback<HealthInfo>) conn -> {
                Properties serverInfo = conn.info("server");
                if (serverInfo == null) return new HealthInfo("DOWN", "unknown", 0);

                String version = serverInfo.getProperty("redis_version", "unknown");
                long uptime = Long.parseLong(
                        serverInfo.getProperty("uptime_in_seconds", "0"));

                return new HealthInfo("UP", version, uptime);
            });
        } catch (Exception e) {
            logger.error("Error fetching Redis health for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(InstanceRef instance) {
        try {
            return redisTemplate.execute((RedisCallback<ResourceStats>) conn -> {
                Properties memInfo = conn.info("memory");
                if (memInfo == null) return new ResourceStats(0, 0, 0);

                long usedMemory = Long.parseLong(
                        memInfo.getProperty("used_memory", "0"));
                long maxMemory = Long.parseLong(
                        memInfo.getProperty("maxmemory", "0"));
                double fragRatio = Double.parseDouble(
                        memInfo.getProperty("mem_fragmentation_ratio", "1.0"));

                if (maxMemory == 0) maxMemory = usedMemory * 3;

                ResourceStats stats = new ResourceStats(0.0, usedMemory, maxMemory);
                stats.setFragmentationRatio(fragRatio);
                return stats;
            });
        } catch (Exception e) {
            logger.error("Error fetching resource stats for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new ResourceStats(0, 0, 0);
        }
    }

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            return redisTemplate.execute((RedisCallback<ConnectionStats>) conn -> {
                Properties clientInfo = conn.info("clients");
                if (clientInfo == null) return new ConnectionStats(0, 0, 0);

                int connectedClients = Integer.parseInt(
                        clientInfo.getProperty("connected_clients", "0"));
                long connErrors = Long.parseLong(
                        clientInfo.getProperty("total_connections_received", "0"));
                long rejectedConn = Long.parseLong(
                        clientInfo.getProperty("rejected_connections", "0"));

                return new ConnectionStats(connectedClients, connErrors, rejectedConn);
            });
        } catch (Exception e) {
            logger.error("Error fetching connection stats for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new ConnectionStats(0, 0, 0);
        }
    }

    @Override
    public DataStats getDataStats(InstanceRef instance) {
        try {
            return redisTemplate.execute((RedisCallback<DataStats>) conn -> {
                Properties memInfo = conn.info("memory");
                Properties keyInfo = conn.info("keyspace");
                if (memInfo == null) return new DataStats(0, 0, 0);

                long usedMemory = Long.parseLong(
                        memInfo.getProperty("used_memory", "0"));
                long maxMemory = Long.parseLong(
                        memInfo.getProperty("maxmemory", "0"));
                if (maxMemory == 0) maxMemory = usedMemory * 3;

                double fragRatio = Double.parseDouble(
                        memInfo.getProperty("mem_fragmentation_ratio", "1.0"));

                // Keyspace'den toplam key sayısı
                long totalKeys = 0;
                if (keyInfo != null) {
                    for (String key : keyInfo.stringPropertyNames()) {
                        if (key.startsWith("db")) {
                            String val = keyInfo.getProperty(key);
                            for (String part : val.split(",")) {
                                if (part.startsWith("keys=")) {
                                    totalKeys += Long.parseLong(part.split("=")[1]);
                                }
                            }
                        }
                    }
                }

                DataStats stats = new DataStats(totalKeys, usedMemory, maxMemory);
                stats.setFragmentationRatio(fragRatio);
                return stats;
            });
        } catch (Exception e) {
            logger.error("Error fetching data stats for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new DataStats(0, 0, 0);
        }
    }

    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {
        logger.debug("Redis doesn't support cluster info for instance: {}",
                instance.getName());
        return null;
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance,
                                      String prefix,
                                      String cursor,
                                      int count) {
        List<KeyInfo> keys = new ArrayList<>();
        // "İçeren" arama: *prefix*  (prefix boşsa tüm key'ler)
        final String search = (prefix == null) ? "" : prefix;
        String pattern = search.isEmpty() ? "*" : "*" + search + "*";
        final String startCursor = (cursor == null || cursor.isEmpty()) ? "0" : cursor;

        try {
            return redisTemplate.execute((RedisCallback<PageInfo<KeyInfo>>) conn -> {
                Object nativeConn = conn.getNativeConnection();
                if (!(nativeConn instanceof io.lettuce.core.api.async.RedisAsyncCommands)) {
                    logger.warn("Native connection is not RedisAsyncCommands, cannot SCAN");
                    return new PageInfo<>(new ArrayList<>(), "0", false, count);
                }

                @SuppressWarnings("unchecked")
                io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]> async =
                        (io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]>) nativeConn;
                io.lettuce.core.api.sync.RedisCommands<byte[], byte[]> sync =
                        async.getStatefulConnection().sync();

                io.lettuce.core.ScanCursor scanCursor = io.lettuce.core.ScanCursor.of(startCursor);
                io.lettuce.core.ScanArgs args = io.lettuce.core.ScanArgs.Builder
                        .matches(pattern).limit(count);

                io.lettuce.core.KeyScanCursor<byte[]> result = sync.scan(scanCursor, args);

                for (byte[] keyBytes : result.getKeys()) {
                    String keyName = new String(keyBytes, StandardCharsets.UTF_8);
                    String type = sync.type(keyBytes);
                    Long ttlSec = sync.ttl(keyBytes);
                    long ttl = (ttlSec != null && ttlSec >= 0) ? ttlSec * 1000
                            : (ttlSec != null ? ttlSec : -2);
                    keys.add(new KeyInfo(keyName, type, 0, ttl));
                }

                // "Öncelik başındakiler": arama terimiyle BAŞLAYAN key'ler önce,
                // sadece İÇEREN key'ler sonra. Her grup kendi içinde alfabetik.
                if (!search.isEmpty()) {
                    keys.sort((a, b) -> {
                        boolean aStarts = a.getKeyName().startsWith(search);
                        boolean bStarts = b.getKeyName().startsWith(search);
                        if (aStarts != bStarts) {
                            return aStarts ? -1 : 1;   // başlayan öne
                        }
                        return a.getKeyName().compareTo(b.getKeyName()); // ikincil: alfabetik
                    });
                }

                String nextCursor = result.getCursor();
                boolean hasMore = !result.isFinished();

                logger.debug("Scanned {} keys, pattern '{}', cursor {} -> {}, hasMore={}",
                        keys.size(), pattern, startCursor, nextCursor, hasMore);

                return new PageInfo<>(keys, nextCursor, hasMore, count);
            });

        } catch (Exception e) {
            logger.error("Error scanning keys for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new PageInfo<>(new ArrayList<>(), "0", false, count);
        }
    }

    @Override
    public long getKeyTTL(InstanceRef instance, String key) {
        try {
            Long ttlSeconds = redisTemplate.execute((RedisCallback<Long>) conn ->
                    conn.keyCommands().ttl(key.getBytes(StandardCharsets.UTF_8)));

            if (ttlSeconds == null) return -2;
            if (ttlSeconds < 0) return ttlSeconds;
            return ttlSeconds * 1000;
        } catch (Exception e) {
            logger.error("Error fetching TTL for key '{}' on {}: {}",
                    key, instance.getName(), e.getMessage(), e);
            return -2;
        }
    }

    @Override
    public List<KeyInfo> getTopNKeys(InstanceRef instance, int n) {
        List<KeyInfo> keys = new ArrayList<>();

        try {
            redisTemplate.execute((RedisCallback<Void>) conn -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .count(n * 3)
                        .build();

                try (Cursor<byte[]> cursor = conn.scan(options)) {
                    while (cursor.hasNext() && keys.size() < n * 3) {
                        byte[] keyBytes = cursor.next();
                        String keyName = new String(keyBytes, StandardCharsets.UTF_8);
                        // MEMORY USAGE <key> ile gerçek key boyutu
                        long sizeBytes = 0L;
                        try {
                            Object nativeConn = conn.getNativeConnection();
                            if (nativeConn instanceof io.lettuce.core.api.async.RedisAsyncCommands) {
                                @SuppressWarnings("unchecked")
                                io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]> async =
                                        (io.lettuce.core.api.async.RedisAsyncCommands<byte[], byte[]>) nativeConn;
                                // async komuttan sync komutlara geç
                                io.lettuce.core.api.sync.RedisCommands<byte[], byte[]> sync =
                                        async.getStatefulConnection().sync();
                                Long usage = sync.memoryUsage(keyBytes);
                                if (usage != null) sizeBytes = usage;
                            }
                        } catch (Exception ex) {
                            logger.warn("MEMORY USAGE failed for key '{}': {}",
                                    new String(keyBytes, StandardCharsets.UTF_8), ex.getMessage());
                        }

                        String type = conn.type(keyBytes).code();
                        long ttl = conn.ttl(keyBytes) * 1000;
                        keys.add(new KeyInfo(keyName, type, sizeBytes, ttl));
                    }
                }
                return null;
            });

            logger.debug("Fetched {} candidate keys for top-{} analysis on instance {}",
                    keys.size(), n, instance.getName());

        } catch (Exception e) {
            logger.error("Error fetching top N keys for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new ArrayList<>();
        }

        keys.sort((a, b) -> Long.compare(b.getSizeBytes(), a.getSizeBytes()));
        return keys.stream().limit(n).toList();
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(InstanceRef instance) {
        try {
            return redisTemplate.execute((RedisCallback<CacheEfficiencyStats>) conn -> {
                Properties stats = conn.info("stats");
                if (stats == null) return new CacheEfficiencyStats(0, 0);

                long hits = Long.parseLong(stats.getProperty("keyspace_hits", "0"));
                long misses = Long.parseLong(stats.getProperty("keyspace_misses", "0"));

                return new CacheEfficiencyStats(hits, misses);
            });
        } catch (Exception e) {
            logger.error("Error fetching cache efficiency for {}: {}",
                    instance.getName(), e.getMessage(), e);
            return new CacheEfficiencyStats(0, 0);
        }
    }

}