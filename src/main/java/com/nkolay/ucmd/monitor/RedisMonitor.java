package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class RedisMonitor implements CacheMonitor {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisConnectionFactory connectionFactory;

    public RedisMonitor(RedisTemplate<String, String> redisTemplate,
                        RedisConnectionFactory connectionFactory) {
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
    }

    private static final Logger logger = LoggerFactory.getLogger(RedisMonitor.class);

    @Override
    public String getSupportedType() {
        return "REDIS";
    }

    @Override
    public boolean isReachable(InstanceRef instance) {
        try {
            RedisConnection conn = connectionFactory.getConnection();
            conn.ping();
            conn.close();
            return true;
        } catch (Exception e) {
            logger.warn("Hazelcast reachability check failed for {}: {}",
                    instance.getName(), e.getMessage());

            return false;
        }
    }

    @Override
    public HealthInfo getHealth(InstanceRef instance) {
        try {
            Map<String, String> info = parseInfo(getInfoSection("server"));

            String version = info.getOrDefault("redis_version", "unknown");
            long uptime = Long.parseLong(
                    info.getOrDefault("uptime_in_seconds", "0")
            );

            return new HealthInfo("UP", version, uptime);
        } catch (Exception e) {
            logger.error("Error fetching Redis health: {}", e.getMessage(), e);

            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(InstanceRef instance) {
        try {
         Map<String, String> info = parseInfo(getInfoSection("all"));

         long usedMemory = Long.parseLong(
                 info.getOrDefault("used_memory", "0")
         );

        long maxMemory = Long.parseLong(
                info.getOrDefault("maxmemory", "0")
        );

        double fragRatio = Double.parseDouble(
                info.getOrDefault("mem_fragmentation_ratio", "1.0")
        );

        ResourceStats stats = new ResourceStats(0.0, usedMemory, maxMemory);
        stats.setFragmentationRatio(fragRatio);
        return stats;
        } catch (Exception e) {
            logger.error("Error fetching resource stats: {}", e.getMessage(), e);

            return new ResourceStats(0, 0, 0);
        }
    }

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            Map<String, String> info = parseInfo(getInfoSection("clients"));

            int connectedClients = Integer.parseInt(
                    info.getOrDefault("connected_clients", "0")
            );
            long connErrors = Long.parseLong(
                    info.getOrDefault("total_connection_received", "0")
            );

            long rejectedConn = Long.parseLong(
                    info.getOrDefault("rejected_connections", "0")
            );

            return new ConnectionStats(connectedClients, connErrors, rejectedConn);

        } catch (Exception e) {
            logger.error("Error fetching connection stats: {}", e.getMessage(), e);

            return new ConnectionStats(0,0,0);
        }
    }

    @Override
    public DataStats getDataStats(InstanceRef instance) {
        try {
            Map<String, String> memInfo = parseInfo(getInfoSection("memory"));
            Map<String, String> keyInfo = parseInfo(getInfoSection("keyspace"));

            long usedMemory = Long.parseLong(
                    memInfo.getOrDefault("used_memory", "0")
            );
            long maxMemory = Long.parseLong(
                    memInfo.getOrDefault("maxmemory", "0")
            );
            if (maxMemory == 0) maxMemory = usedMemory * 3;

            // Keyspace'den toplam key sayısı
            long totalKeys = 0;
            for (Map.Entry<String, String> entry : keyInfo.entrySet()) {
                if (entry.getKey().startsWith("db")) {
                    // Format: keys=N,expires=M,avg_ttl=X
                    String val = entry.getValue();
                    String[] parts = val.split(",");
                    for (String part : parts) {
                        if (part.startsWith("keys=")) {
                            totalKeys += Long.parseLong(part.split("=")[1]);
                        }
                    }
                }
            }

            DataStats stats = new DataStats(totalKeys, usedMemory, maxMemory);
            double fragRatio = Double.parseDouble(
                    memInfo.getOrDefault("mem_fragmentation_ratio", "1.0")
            );
            stats.setFragmentationRatio(fragRatio);
            return stats;
        } catch (Exception e) {
            logger.error("Error fetching data stats: {}", e.getMessage(), e);

            return new DataStats(0, 0, 0);
        }
    }

    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {
        logger.debug("Redis doesn't support cluster info for instance: {}", instance.getName());
        return null;
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor, int count) {
        List<KeyInfo> keys = new ArrayList<>();
        String pattern = (prefix == null || prefix.isEmpty())
                ? "*" : prefix + "*";

        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(count)
                    .build();

            Cursor<byte[]> scanCursor = redisTemplate
                    .getConnectionFactory()
                    .getConnection()
                    .scan(options);

            int fetched = 0;
            while (scanCursor.hasNext() && fetched < count) {
                byte[] keyBytes = scanCursor.next();
                String keyName = new String(keyBytes, StandardCharsets.UTF_8);

                String type = redisTemplate.type(keyName).code();
                long ttl = redisTemplate.getExpire(keyName) * 1000;

                KeyInfo keyInfo = new KeyInfo(keyName, type, 0, ttl);
                keys.add(keyInfo);
                fetched++;
            }

            String nextCursor = scanCursor.getCursorId() + "";
            boolean hasMore = !nextCursor.equals("0");
            scanCursor.close();

            return new PageInfo<>(keys, nextCursor, hasMore, count);

        } catch (Exception e) {
            logger.error("Error scanning keys: {}", e.getMessage(), e);
            return new PageInfo<>(new ArrayList<>(), "0", false, count);
        }
    }

    @Override
    public List<KeyInfo> getTopNKeys(InstanceRef instance, int n) {
        List<KeyInfo> keys = new ArrayList<>();

        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .count(n * 3)
                    .build();

            Cursor<byte[]> cursor = redisTemplate
                    .getConnectionFactory()
                    .getConnection()
                    .scan(options);

            while (cursor.hasNext() && keys.size() < n * 3) {
                String keyName = new String(
                        cursor.next(), StandardCharsets.UTF_8
                );
                // MEMORY USAGE komutu — byte cinsinden boyut
                Long size = redisTemplate.execute((RedisCallback<Long>) conn -> {
                    Object raw = conn.execute("MEMORY",
                            "USAGE".getBytes(StandardCharsets.UTF_8),
                            keyName.getBytes(StandardCharsets.UTF_8));
                    return (raw instanceof Long) ? (Long) raw : 0L;
                });
                long sizeBytes = (size != null) ? size : 0;
                String type = redisTemplate.type(keyName).code();
                long ttl = redisTemplate.getExpire(keyName) * 1000;

                keys.add(new KeyInfo(keyName, type, sizeBytes, ttl));
            }
            cursor.close();

        } catch (Exception e) {
            logger.error("Error fetching top N keys: {}", e.getMessage(), e);
            return new ArrayList<>();
        }

        // Boyuta göre azalan sırala, en büyük N'i al
        keys.sort((a, b) -> Long.compare(b.getSizeBytes(), a.getSizeBytes()));
        return keys.stream().limit(n).toList();
    }

    // ===== YARDIMCI METOTLAR =====

    /**
     * Redis INFO komutunu çalıştır, belirli section'ı döndür
     * section: server | clients | memory | keyspace | all
     */
    private String getInfoSection(String section) {
        return redisTemplate.execute((RedisCallback<String>) conn -> {
            Properties props = conn.serverCommands().info(section);
            if (props == null) return "";
            StringBuilder sb = new StringBuilder();
            for (String name : props.stringPropertyNames()) {
                sb.append(name).append(":").append(props.getProperty(name)).append("\r\n");
            }
            return sb.toString();
        });
    }

    /**
     * INFO çıktısını key:value map'e dönüştür
     */
    private Map<String, String> parseInfo(String info) {
        Map<String, String> map = new HashMap<>();
        if (info == null || info.isEmpty()) return map;

        for (String line : info.split("\r\n")) {
            if (line.startsWith("#") || line.isEmpty()) continue;
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                map.put(parts[0].trim(), parts[1].trim());
            }
        }
        return map;
    }


}
