package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisMonitor implements CacheMonitor {

    private static final Logger logger = LoggerFactory.getLogger(RedisMonitor.class);

    private final RedisConnectionManager connectionManager;

    // CPU delta hesabı: instanceId -> [cpuSeconds, timestampMillis]
    private final Map<Long, double[]> lastCpuSample = new ConcurrentHashMap<>();

    public RedisMonitor(RedisConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public String getSupportedType() {
        return "REDIS";
    }

    // ═══════════════ Yardımcılar ═══════════════

    private RedisCommands<byte[], byte[]> commandsFor(InstanceRef instance) {
        var conn = connectionManager.getConnection(instance.getHost(), instance.getPort());
        return conn != null ? conn.sync() : null;
    }

    private String infoValue(String info, String field, String defaultValue) {
        if (info == null) return defaultValue;
        for (String line : info.split("\r?\n")) {
            if (line.startsWith(field + ":")) {
                return line.substring(field.length() + 1).trim();
            }
        }
        return defaultValue;
    }

    private long infoLong(String info, String field, long defaultValue) {
        try {
            return Long.parseLong(infoValue(info, field, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double infoDouble(String info, String field, double defaultValue) {
        try {
            return Double.parseDouble(infoValue(info, field, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private long safeMemoryUsage(RedisCommands<byte[], byte[]> cmd, byte[] key) {
        try {
            Long usage = cmd.memoryUsage(key);
            return usage != null ? usage : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ═══════════════ Sağlık ═══════════════

    @Override
    public boolean isReachable(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return false;
            return "PONG".equalsIgnoreCase(cmd.ping());
        } catch (Exception e) {
            logger.warn("Redis reachability check failed for {}: {}",
                    instance.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public HealthInfo getHealth(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return new HealthInfo("DOWN", "unknown", 0);

            String info = cmd.info("server");
            String version = infoValue(info, "redis_version", "unknown");
            long uptime = infoLong(info, "uptime_in_seconds", 0);

            return new HealthInfo("UP", version, uptime);

        } catch (Exception e) {
            logger.error("Error fetching Redis health for {}: {}",
                    instance.getName(), e.getMessage());
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    // ═══════════════ Kaynaklar ═══════════════

    @Override
    public ResourceStats getResourceStats(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return new ResourceStats(0, 0, 0);

            String memInfo = cmd.info("memory");
            String cpuInfo = cmd.info("cpu");

            long usedMemory = infoLong(memInfo, "used_memory", 0);
            long maxMemory = infoLong(memInfo, "maxmemory", 0);
            long usedMemoryRss = infoLong(memInfo, "used_memory_rss", 0);
            double fragRatio = infoDouble(memInfo, "mem_fragmentation_ratio", 1.0);

            double cpuPercent = calculateCpuPercent(instance.getId(), cpuInfo);

            ResourceStats stats = new ResourceStats(cpuPercent, usedMemory, maxMemory);
            stats.setFragmentationRatio(fragRatio);
            stats.setMemoryRssBytes(usedMemoryRss);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching resource stats for {}: {}",
                    instance.getName(), e.getMessage());
            return new ResourceStats(0, 0, 0);
        }
    }

    private double calculateCpuPercent(Long instanceId, String cpuInfo) {
        if (cpuInfo == null || instanceId == null) return 0.0;
        try {
            double sys = infoDouble(cpuInfo, "used_cpu_sys", 0);
            double user = infoDouble(cpuInfo, "used_cpu_user", 0);
            double totalCpuSeconds = sys + user;
            long now = System.currentTimeMillis();

            double[] previous = lastCpuSample.get(instanceId);
            lastCpuSample.put(instanceId, new double[]{totalCpuSeconds, now});

            if (previous == null) return 0.0;

            double cpuDelta = totalCpuSeconds - previous[0];
            double timeDeltaSeconds = (now - previous[1]) / 1000.0;
            if (timeDeltaSeconds <= 0) return 0.0;

            double percent = (cpuDelta / timeDeltaSeconds) * 100.0;
            if (percent < 0) return 0.0;
            if (percent > 100) return 100.0;

            return Math.round(percent * 100.0) / 100.0;

        } catch (Exception e) {
            logger.warn("CPU delta hesaplanamadı: {}", e.getMessage());
            return 0.0;
        }
    }

    // ═══════════════ Bağlantılar ═══════════════

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return new ConnectionStats(0, 0, 0);

            String clientInfo = cmd.info("clients");
            String statsInfo = cmd.info("stats");

            int connectedClients = (int) infoLong(clientInfo, "connected_clients", 0);
            long totalConnections = infoLong(statsInfo, "total_connections_received", 0);
            long rejectedConn = infoLong(statsInfo, "rejected_connections", 0);

            return new ConnectionStats(connectedClients, totalConnections, rejectedConn);

        } catch (Exception e) {
            logger.error("Error fetching connection stats for {}: {}",
                    instance.getName(), e.getMessage());
            return new ConnectionStats(0, 0, 0);
        }
    }

    // ═══════════════ Veri istatistikleri ═══════════════

    @Override
    public DataStats getDataStats(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return new DataStats(0, 0, 0);

            String memInfo = cmd.info("memory");
            String keyspaceInfo = cmd.info("keyspace");

            long usedMemory = infoLong(memInfo, "used_memory", 0);
            long maxMemory = infoLong(memInfo, "maxmemory", 0);
            double fragRatio = infoDouble(memInfo, "mem_fragmentation_ratio", 1.0);

            // keyspace: "db0:keys=1234,expires=100,avg_ttl=0"
            long totalKeys = 0;
            if (keyspaceInfo != null) {
                for (String line : keyspaceInfo.split("\r?\n")) {
                    if (line.startsWith("db")) {
                        for (String part : line.split(",")) {
                            int idx = part.indexOf("keys=");
                            if (idx >= 0) {
                                try {
                                    totalKeys += Long.parseLong(
                                            part.substring(idx + 5).trim());
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }

            DataStats stats = new DataStats(totalKeys, usedMemory, maxMemory);
            stats.setFragmentationRatio(fragRatio);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching data stats for {}: {}",
                    instance.getName(), e.getMessage());
            return new DataStats(0, 0, 0);
        }
    }

    // ═══════════════ Cluster (Redis'te yok) ═══════════════

    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {
        logger.debug("Redis doesn't support cluster info for instance: {}",
                instance.getName());
        return null;
    }

    // ═══════════════ Cache verimliliği ═══════════════

    @Override
    public CacheEfficiencyStats getCacheEfficiency(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return new CacheEfficiencyStats(0, 0);

            String statsInfo = cmd.info("stats");
            long hits = infoLong(statsInfo, "keyspace_hits", 0);
            long misses = infoLong(statsInfo, "keyspace_misses", 0);

            return new CacheEfficiencyStats(hits, misses);

        } catch (Exception e) {
            logger.error("Error fetching cache efficiency for {}: {}",
                    instance.getName(), e.getMessage());
            return new CacheEfficiencyStats(0, 0);
        }
    }

    // ═══════════════ Key tarama (sayfalamalı) ═══════════════

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance,
                                      String prefix,
                                      String cursor,
                                      int count) {
        List<KeyInfo> keys = new ArrayList<>();
        final String search = (prefix == null) ? "" : prefix;
        String pattern = search.isEmpty() ? "*" : "*" + search + "*";
        final String startCursor = (cursor == null || cursor.isEmpty()) ? "0" : cursor;

        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) {
                return new PageInfo<>(new ArrayList<>(), "0", false, count);
            }

            ScanCursor scanCursor = ScanCursor.of(startCursor);
            ScanArgs args = ScanArgs.Builder.matches(pattern).limit(count);
            KeyScanCursor<byte[]> result = cmd.scan(scanCursor, args);

            for (byte[] keyBytes : result.getKeys()) {
                String keyName = new String(keyBytes, StandardCharsets.UTF_8);
                String type = cmd.type(keyBytes);
                Long ttlSec = cmd.ttl(keyBytes);
                long ttl = (ttlSec != null && ttlSec >= 0) ? ttlSec * 1000
                        : (ttlSec != null ? ttlSec : -2);
                long sizeBytes = safeMemoryUsage(cmd, keyBytes);

                keys.add(new KeyInfo(keyName, type, sizeBytes, ttl));
            }

            // Arama terimiyle BAŞLAYANLAR öne (sayfa içi sıralama)
            if (!search.isEmpty()) {
                keys.sort((a, b) -> {
                    boolean aStarts = a.getKeyName().startsWith(search);
                    boolean bStarts = b.getKeyName().startsWith(search);
                    if (aStarts != bStarts) return aStarts ? -1 : 1;
                    return a.getKeyName().compareTo(b.getKeyName());
                });
            }

            String nextCursor = result.getCursor();
            boolean hasMore = !result.isFinished();

            logger.debug("Scanned {} keys on {} (pattern '{}', cursor {} -> {})",
                    keys.size(), instance.getName(), pattern, startCursor, nextCursor);

            return new PageInfo<>(keys, nextCursor, hasMore, count);

        } catch (Exception e) {
            logger.error("Error scanning keys for {}: {}",
                    instance.getName(), e.getMessage());
            return new PageInfo<>(new ArrayList<>(), "0", false, count);
        }
    }

    // ═══════════════ En büyük N key ═══════════════

    @Override
    public List<KeyInfo> getTopNKeys(InstanceRef instance, int n) {
        List<KeyInfo> keys = new ArrayList<>();

        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return keys;

            ScanCursor cursor = ScanCursor.INITIAL;
            ScanArgs args = ScanArgs.Builder.limit(500);
            int scanned = 0;
            final int MAX_SCAN = Math.max(n * 20, 2000);   // güvenlik sınırı

            do {
                KeyScanCursor<byte[]> page = cmd.scan(cursor, args);

                for (byte[] keyBytes : page.getKeys()) {
                    String keyName = new String(keyBytes, StandardCharsets.UTF_8);
                    String type = cmd.type(keyBytes);
                    Long ttlSec = cmd.ttl(keyBytes);
                    long ttl = (ttlSec != null && ttlSec >= 0) ? ttlSec * 1000
                            : (ttlSec != null ? ttlSec : -2);
                    long sizeBytes = safeMemoryUsage(cmd, keyBytes);

                    keys.add(new KeyInfo(keyName, type, sizeBytes, ttl));
                    scanned++;
                }

                cursor = ScanCursor.of(page.getCursor());
                if (page.isFinished()) break;

            } while (scanned < MAX_SCAN);

            logger.debug("Scanned {} keys for top-{} on {}",
                    scanned, n, instance.getName());

        } catch (Exception e) {
            logger.error("Error fetching top N keys for {}: {}",
                    instance.getName(), e.getMessage());
            return new ArrayList<>();
        }

        keys.sort((a, b) -> Long.compare(b.getSizeBytes(), a.getSizeBytes()));
        return keys.stream().limit(n).toList();
    }

    // ═══════════════ Key TTL ═══════════════

    @Override
    public long getKeyTTL(InstanceRef instance, String key) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return -2;

            Long ttlSeconds = cmd.ttl(key.getBytes(StandardCharsets.UTF_8));
            if (ttlSeconds == null) return -2;
            if (ttlSeconds < 0) return ttlSeconds;      // -1 kalıcı, -2 yok
            return ttlSeconds * 1000;

        } catch (Exception e) {
            logger.error("Error fetching TTL for key '{}' on {}: {}",
                    key, instance.getName(), e.getMessage());
            return -2;
        }
    }

    // ═══════════════ Key desen istatistikleri ═══════════════

    @Override
    public List<KeyPatternStats> getKeyPatternStats(InstanceRef instance,
                                                    List<KeyPatternConfig.PatternDef> patterns) {
        List<KeyPatternStats> result = new ArrayList<>();
        for (KeyPatternConfig.PatternDef def : patterns) {
            result.add(new KeyPatternStats(def.getPattern(), def.getLabel()));
        }
        KeyPatternStats other = new KeyPatternStats("*", "Diğer");

        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return result;

            ScanCursor cursor = ScanCursor.INITIAL;
            ScanArgs args = ScanArgs.Builder.limit(500);
            int scanned = 0;
            final int MAX_KEYS = 50_000;

            do {
                KeyScanCursor<byte[]> page = cmd.scan(cursor, args);

                for (byte[] keyBytes : page.getKeys()) {
                    String keyName = new String(keyBytes, StandardCharsets.UTF_8);
                    long size = safeMemoryUsage(cmd, keyBytes);

                    boolean matched = false;
                    for (KeyPatternStats stats : result) {
                        if (matchesPattern(keyName, stats.getPattern())) {
                            stats.increment(size);
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) other.increment(size);
                    scanned++;
                }

                cursor = ScanCursor.of(page.getCursor());
                if (page.isFinished()) break;

            } while (scanned < MAX_KEYS);

            logger.debug("Pattern scan completed: {} keys on {}",
                    scanned, instance.getName());

        } catch (Exception e) {
            logger.error("Error scanning key patterns for {}: {}",
                    instance.getName(), e.getMessage());
        }

        if (other.getKeyCount() > 0) result.add(other);
        result.removeIf(s -> s.getKeyCount() == 0);
        return result;
    }

    /** Redis glob desenini (yalnızca *) regex'e çevirip eşleştirir. */
    private boolean matchesPattern(String keyName, String pattern) {
        if (pattern == null || pattern.equals("*")) return true;
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        return keyName.matches(regex.toString());
    }
}