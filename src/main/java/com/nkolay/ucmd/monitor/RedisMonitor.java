package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.exception.InvalidPrefixException;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.sync.RedisCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    public static final double CPU_NOT_READY = -1.0;

    @Value("${ucmd.key-search.min.length:3}")
    private int minPrefixLength;

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
            if (cmd == null) {
                    ResourceStats down = new ResourceStats(0,0,0);
                    down.setOverallStatus(MetricStatus.UNAVAILABLE);
                    down.setCpuStats(MetricStatus.UNAVAILABLE);
                    down.setMemoryStatus(MetricStatus.UNAVAILABLE);
                    down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
                    return down;
            }

            String memInfo = cmd.info("memory");
            String cpuInfo = cmd.info("cpu");

            long usedMemory = infoLong(memInfo, "used_memory", 0);
            long maxMemory = infoLong(memInfo, "maxmemory", 0);
            long usedMemoryRss = infoLong(memInfo, "used_memory_rss", 0);
            double fragRatio = infoDouble(memInfo, "mem_fragmentation_ratio", 1.0);

            double cpuRaw = calculateCpuPercent(instance.getId(), cpuInfo);
            boolean cpuReady = cpuRaw >= 0;
            double cpuPercent = cpuReady ? cpuRaw : 0.0;

            ResourceStats stats = new ResourceStats(cpuPercent, usedMemory, maxMemory);
            stats.setFragmentationRatio(fragRatio);
            stats.setMemoryRssBytes(usedMemoryRss);

            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.AVAILABLE);
            stats.setFragmentationStatus(MetricStatus.AVAILABLE);

            stats.setCpuStats(cpuReady ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching resource stats for {}: {}",
                    instance.getName(), e.getMessage());
            ResourceStats down = new ResourceStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setCpuStats(MetricStatus.UNAVAILABLE);
            down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
            down.setMemoryStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }


    private double calculateCpuPercent(Long instanceId, String cpuInfo) {
        if (cpuInfo == null || instanceId == null) return CPU_NOT_READY;
        try {
            double sys = infoDouble(cpuInfo, "used_cpu_sys", 0);
            double user = infoDouble(cpuInfo, "used_cpu_user", 0);
            double totalCpuSeconds = sys + user;
            long now = System.currentTimeMillis();

            double[] previous = lastCpuSample.get(instanceId);
            lastCpuSample.put(instanceId, new double[]{totalCpuSeconds, now});

            if (previous == null) return CPU_NOT_READY;

            double cpuDelta = totalCpuSeconds - previous[0];
            double timeDeltaSeconds = (now - previous[1]) / 1000.0;
            if (timeDeltaSeconds <= 0) return CPU_NOT_READY;

            double percent = (cpuDelta / timeDeltaSeconds) * 100.0;
            if (percent < 0) return 0.0;
            if (percent > 100) return 100.0;

            return Math.round(percent * 100.0) / 100.0;

        } catch (Exception e) {
            logger.warn("CPU delta hesaplanamadı: {}", e.getMessage());
            return CPU_NOT_READY;
        }
    }

    // ═══════════════ Bağlantılar ═══════════════

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) {
                ConnectionStats down = new ConnectionStats(0, 0, 0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setActiveClientStatus(MetricStatus.UNAVAILABLE);
                down.setErrorStatus(MetricStatus.UNAVAILABLE);
                return down;
            }
            String clientInfo = cmd.info("clients");
            String statsInfo = cmd.info("stats");

            int connectedClients = (int) infoLong(clientInfo, "connected_clients", 0);
            long rejectedConn = infoLong(statsInfo, "rejected_connections", 0);

            ConnectionStats cs = new ConnectionStats(connectedClients,0, rejectedConn);
            cs.setOverallStatus(MetricStatus.AVAILABLE);
            cs.setActiveClientStatus(MetricStatus.AVAILABLE);
            cs.setErrorStatus(MetricStatus.NOT_APPLICABLE);
            return cs;

        } catch (Exception e) {
            logger.error("Error fetching connection stats for {}: {}",
                    instance.getName(), e.getMessage());
            ConnectionStats down = new ConnectionStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setErrorStatus(MetricStatus.UNAVAILABLE);
            down.setActiveClientStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }

    // ═══════════════ Veri istatistikleri ═══════════════

    @Override
    public DataStats getDataStats(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) {
                DataStats down = new DataStats(0, 0, 0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setMemoryStatus(MetricStatus.UNAVAILABLE);
                down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
                return down;
            }
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
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.AVAILABLE);
            stats.setFragmentationStatus(MetricStatus.AVAILABLE);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching data stats for {}: {}",
                    instance.getName(), e.getMessage());
            DataStats down = new DataStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setMemoryStatus(MetricStatus.UNAVAILABLE);
            down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }

    // ═══════════════ Cluster (Redis'te yok) ═══════════════

    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {

        logger.debug("Redis doesn't support cluster info for instance: {}",
                instance.getName());
        ClusterInfo stats = new ClusterInfo();
                stats.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return stats;
    }

    // ═══════════════ Cache verimliliği ═══════════════

    @Override
    public CacheEfficiencyStats getCacheEfficiency(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) {
                CacheEfficiencyStats down = new CacheEfficiencyStats(0,0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                return down;
            }
            String statsInfo = cmd.info("stats");
            long hits = infoLong(statsInfo, "keyspace_hits", 0);
            long misses = infoLong(statsInfo, "keyspace_misses", 0);

            CacheEfficiencyStats ce = new CacheEfficiencyStats(hits, misses);
            return ce;

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
                                      int count,
                                      MatchMode matchMode) {
        final String search = (prefix == null) ? "" : prefix.trim();
        if (search.isEmpty()) {
            throw new InvalidPrefixException("prefix zorunludur");
        }
        if (search.length() < minPrefixLength) {
            throw new InvalidPrefixException("Prefix uzunluğu en az" + minPrefixLength + "olmalıdır");
        }

        List<KeyInfo> keys = new ArrayList<>();
        MatchMode mode = (matchMode == null) ? MatchMode.STARTS_WITH : matchMode;
        String escaped = escapeGlob(search);
        String pattern = (mode == MatchMode.CONTAINS) ? "*" + escaped + "*" : escaped + "*";
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

                KeyInfo ki = new KeyInfo(keyName, type, sizeBytes, ttl);
                ki.setSizeStatus(MetricStatus.AVAILABLE);
                keys.add(ki);
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

            logger.debug("Scanned {} keys on {} (cursor {} -> {})",
                    keys.size(), instance.getName(), startCursor, nextCursor);

            return new PageInfo<>(keys, nextCursor, hasMore, count);

        } catch (InvalidPrefixException e) {
            throw e;
        }
        catch (Exception e) {
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

                    KeyInfo ki = new KeyInfo(keyName, type, sizeBytes, ttl);
                    ki.setSizeStatus(MetricStatus.AVAILABLE);
                    keys.add(ki);
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

    private String escapeGlob(String input) {
        StringBuilder sb = new StringBuilder(input.length() + 8);
        for (char c : input.toCharArray()) {
            if (c == '*' || c == '?' || c == '[' || c == ']' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
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