package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.exception.InvalidPrefixException;
import com.nkolay.ucmd.security.KeyMasker;
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
    private final KeySecurityConfig keySecurityConfig;
    private final KeyMasker keyMasker;

    // CPU delta hesabı: instanceId -> [cpuSeconds, timestampMillis]
    private final Map<Long, double[]> lastCpuSample = new ConcurrentHashMap<>();

    public static final double CPU_NOT_READY = -1.0;

    @Value("${ucmd.key-search.min-prefix-length:3}")
    private int minPrefixLength;

    private static final int ABSOLUTE_SCAN_LIMIT = 50_000;

    public RedisMonitor(RedisConnectionManager connectionManager,
                        KeySecurityConfig keySecurityConfig,
                        KeyMasker keyMasker) {
        this.connectionManager = connectionManager;
        this.keySecurityConfig = keySecurityConfig;
        this.keyMasker = keyMasker;
    }

    @Override
    public String getSupportedType() {
        return "REDIS";
    }



    // ═══════════════ Yardımcılar ═══════════════

    private RedisCommands<byte[], byte[]> commandsFor(InstanceRef instance) {
        var conn = connectionManager.getConnection(instance);
        return conn != null ? conn.sync() : null;
    }

    /**
     * Rehber §9 — ACL kullanıcısı bir komuta yetkisizse Redis NOPERM döner.
     * Bu durum erişilemezlikten ayrılmalı: PERMISSION_DENIED.
     */
    private MetricStatus failureStatus(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("NOPERM") || msg.contains("WRONGPASS")
                || msg.contains("NOAUTH"))) {
            return MetricStatus.PERMISSION_DENIED;
        }
        return MetricStatus.UNAVAILABLE;
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
                    applyJvmNotApplicable(down);
                    return down;
            }

            String memInfo = cmd.info("memory");
            String cpuInfo = cmd.info("cpu");

            long usedMemory = infoLong(memInfo, "used_memory", 0);
            long maxMemory = infoLong(memInfo, "maxmemory", 0);
            long usedMemoryRss = infoLong(memInfo, "used_memory_rss", 0);
            double fragRatio = infoDouble(memInfo, "mem_fragmentation_ratio", 1.0);
            long fragBytes = infoLong(memInfo, "mem_fragmentation_bytes", 0);

            double cpuRaw = calculateCpuPercent(instance.getId(), cpuInfo);
            boolean cpuReady = cpuRaw >= 0;
            double cpuPercent = cpuReady ? cpuRaw : 0.0;

            ResourceStats stats = new ResourceStats(cpuPercent, usedMemory, maxMemory);
            stats.setFragmentationRatio(fragRatio);
            stats.setFragmentationBytes(fragBytes);
            stats.setMemoryRssBytes(usedMemoryRss);

            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.AVAILABLE);
            stats.setFragmentationStatus(MetricStatus.AVAILABLE);

            stats.setCpuStats(cpuReady ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            applyJvmNotApplicable(stats);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching resource stats for {}: {}",
                    instance.getName(), e.getMessage());
            MetricStatus status = failureStatus(e);
            ResourceStats down = new ResourceStats(0,0,0);
            down.setOverallStatus(status);
            down.setCpuStats(status);
            down.setFragmentationStatus(status);
            down.setMemoryStatus(status);
            applyJvmNotApplicable(down);
            return down;
        }
    }

    /** Rehber §5.6 JVM metrikleri Redis'te yoktur — alanlar null, statüler N/A. */
    private void applyJvmNotApplicable(ResourceStats stats) {
        stats.setJvmStatus(MetricStatus.NOT_APPLICABLE);
        stats.setGcStatus(MetricStatus.NOT_APPLICABLE);
        stats.setThreadStatus(MetricStatus.NOT_APPLICABLE);
        stats.setFileDescriptorStatus(MetricStatus.NOT_APPLICABLE);
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
                down.setMaxClientsStatus(MetricStatus.UNAVAILABLE);
                down.setBlockedClientsStatus(MetricStatus.UNAVAILABLE);
                return down;
            }
            String clientInfo = cmd.info("clients");
            String statsInfo = cmd.info("stats");

            int connectedClients = (int) infoLong(clientInfo, "connected_clients", 0);
            int blockedClients = (int) infoLong(clientInfo, "blocked_clients", 0);
            long maxClients = infoLong(clientInfo, "maxclients", 0);
            long rejectedConn = infoLong(statsInfo, "rejected_connections", 0);
            long totalReceived = infoLong(statsInfo, "total_connections_received", 0);

            ConnectionStats cs = new ConnectionStats(connectedClients, 0, rejectedConn);
            cs.setBlockedClients(blockedClients);
            cs.setMaxClients(maxClients);
            cs.setTotalConnectionsReceived(totalReceived);

            cs.setOverallStatus(MetricStatus.AVAILABLE);
            cs.setActiveClientStatus(MetricStatus.AVAILABLE);
            cs.setBlockedClientsStatus(MetricStatus.AVAILABLE);
            cs.setMaxClientsStatus(maxClients > 0
                    ? MetricStatus.AVAILABLE : MetricStatus.NOT_APPLICABLE);

            // Redis'te "connection error" sayacı yoktur — rejected_connections
            // ayrı bir metrik olarak sunuluyor, bu alan N/A.
            cs.setErrorStatus(MetricStatus.NOT_APPLICABLE);
            return cs;

        } catch (Exception e) {
            logger.error("Error fetching connection stats for {}: {}",
                    instance.getName(), e.getMessage());
            MetricStatus status = failureStatus(e);
            ConnectionStats down = new ConnectionStats(0, 0, 0);
            down.setOverallStatus(status);
            down.setActiveClientStatus(status);
            down.setErrorStatus(status);
            down.setMaxClientsStatus(status);
            down.setBlockedClientsStatus(status);
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

            String statsInfo = cmd.info("stats");
            long fragBytes = infoLong(memInfo, "mem_fragmentation_bytes", 0);

            KeyspaceSummary ks = parseKeyspace(keyspaceInfo);

            DataStats stats = new DataStats(ks.totalKeys(), usedMemory, maxMemory);
            stats.setFragmentationRatio(fragRatio);
            stats.setFragmentationBytes(fragBytes);
            stats.setKeysPerDatabase(ks.perDatabase());
            stats.setKeysWithExpiry(ks.totalExpires());
            stats.setAvgTtlMillis(ks.avgTtlMillis());

            // Rehber §4.3 — eviction / expiration
            stats.setEvictedKeys(infoLong(statsInfo, "evicted_keys", 0));
            stats.setExpiredKeys(infoLong(statsInfo, "expired_keys", 0));

            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setFragmentationStatus(MetricStatus.AVAILABLE);
            stats.setEvictionStatus(MetricStatus.AVAILABLE);
            stats.setTtlStatus(ks.avgTtlMillis() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.NOT_APPLICABLE);
            // memoryStatus constructor'da set edildi (maxmemory=0 → NOT_APPLICABLE)
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching data stats for {}: {}",
                    instance.getName(), e.getMessage());
            MetricStatus status = failureStatus(e);
            DataStats down = new DataStats(0,0,0);
            down.setOverallStatus(status);
            down.setMemoryStatus(status);
            down.setFragmentationStatus(status);
            down.setEvictionStatus(status);
            down.setTtlStatus(status);
            return down;
        }
    }

    // ═══════════════ Cluster (Redis'te yok) ═══════════════

    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {

        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) {
                ClusterInfo down = new ClusterInfo();
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                return down;
            }

            // Rehber §4.7 — cluster_enabled kontrolü
            String clusterInfo = cmd.info("cluster");
            boolean clusterEnabled = infoLong(clusterInfo, "cluster_enabled", 0) == 1;

            ClusterInfo info = new ClusterInfo();
            if (!clusterEnabled) {
                info.setOverallStatus(MetricStatus.NOT_APPLICABLE);
                return info;
            }

            // Cluster modu aktif — CLUSTER INFO desteği henüz eklenmedi
            info.setOverallStatus(MetricStatus.NOT_READY);
            info.setPartitionCountStatus(MetricStatus.NOT_READY);
            info.setBalancedStatus(MetricStatus.NOT_READY);
            return info;

        } catch (Exception e) {
            logger.warn("Cluster info check failed for {}: {}",
                    instance.getName(), e.getMessage());
            ClusterInfo down = new ClusterInfo();
            down.setOverallStatus(failureStatus(e));
            return down;
        }
    }

    /** Redis'te "map" kavramı yoktur — keyspace istatistikleri getDataStats'ta. */
    @Override
    public MapStatsInfo getMapStats(InstanceRef instance) {
        return MapStatsInfo.notApplicable();
    }

    /** Redis tek thread'li çalışır — operation/event kuyruğu kavramı yoktur. */
    @Override
    public OperationStatsInfo getOperationStats(InstanceRef instance) {
        return OperationStatsInfo.notApplicable();
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

            // Rehber §4.4 — throughput ve network
            ce.setInstantaneousOpsPerSec(infoLong(statsInfo, "instantaneous_ops_per_sec", 0));
            ce.setTotalCommandsProcessed(infoLong(statsInfo, "total_commands_processed", 0));
            ce.setInputKbps(infoDouble(statsInfo, "instantaneous_input_kbps", 0));
            ce.setOutputKbps(infoDouble(statsInfo, "instantaneous_output_kbps", 0));
            ce.setTotalErrorReplies(infoLong(statsInfo, "total_error_replies", 0));

            ce.setOverallStatus(MetricStatus.AVAILABLE);
            ce.setThroughputStatus(MetricStatus.AVAILABLE);
            ce.setNetworkStatus(MetricStatus.AVAILABLE);
            return ce;

        } catch (Exception e) {
            logger.error("Error fetching cache efficiency for {}: {}",
                    instance.getName(), e.getMessage());
            MetricStatus status = failureStatus(e);
            CacheEfficiencyStats down = new CacheEfficiencyStats(0, 0);
            down.setOverallStatus(status);
            down.setHitRatioStatus(status);
            down.setThroughputStatus(status);
            down.setNetworkStatus(status);
            return down;
        }
    }

    // ═══════════════ Key tarama (sayfalamalı) ═══════════════

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance,
                                      String prefix,
                                      String cursor,
                                      int count,
                                      MatchMode matchMode,
                                      String mapName) {
        // mapName Hazelcast'e özgüdür, Redis'te yok sayılır.
        final String search = (prefix == null) ? "" : prefix.trim();
        if (search.isEmpty()) {
            throw new InvalidPrefixException("prefix zorunludur");
        }
        if (search.length() < minPrefixLength) {
            throw new InvalidPrefixException("Prefix uzunluğu en az" + minPrefixLength + "olmalıdır");
        }
        if (!keySecurityConfig.isPrefixAllowed(search)) {
            throw new InvalidPrefixException("prefix izin verilen listede değil");
        }

        List<KeyInfo> keys = new ArrayList<>();
        MatchMode mode = (matchMode == null) ? MatchMode.STARTS_WITH : matchMode;
        String escaped = escapeGlob(search);
        String pattern = (mode == MatchMode.CONTAINS) ? "*" + escaped + "*" : escaped + "*";
        final String startCursor = (cursor == null || cursor.isEmpty()) ? "0" : cursor;

        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) {
                return PageInfo.unavailable(count);
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

            // Arama terimiyle BAŞLAYANLAR öne (sayfa içi sıralama).
            // Sıralama ham ad üzerinden yapılır, maskeleme sonradan uygulanır.
            if (!search.isEmpty()) {
                keys.sort((a, b) -> {
                    boolean aStarts = a.getKeyName().startsWith(search);
                    boolean bStarts = b.getKeyName().startsWith(search);
                    if (aStarts != bStarts) return aStarts ? -1 : 1;
                    return a.getKeyName().compareTo(b.getKeyName());
                });
            }

            // Rehber §9 — key adları maskelenerek döner
            keys.forEach(ki -> ki.setKeyName(keyMasker.mask(ki.getKeyName())));

            String nextCursor = result.getCursor();
            boolean hasMore = !result.isFinished();

            logger.debug("Scanned {} keys on {}", keys.size(), instance.getName());

            return new PageInfo<>(keys, nextCursor, hasMore, count);

        } catch (InvalidPrefixException e) {
            throw e;
        }
        catch (Exception e) {
            logger.error("Error scanning keys for {}: {}",
                    instance.getName(), e.getMessage());
            return new PageInfo<>(new ArrayList<>(), "0", false, count, failureStatus(e));
        }
    }

    // ═══════════════ En büyük N key ═══════════════

    @Override
    public TopKeysResponse getTopNKeys(InstanceRef instance, int n, Integer scanLimit) {
        List<KeyInfo> keys = new ArrayList<>();
        final int maxScan = (scanLimit != null && scanLimit > 0)
                ? Math.min(scanLimit, ABSOLUTE_SCAN_LIMIT)
                : Math.max(n * 20, 2000);
        int scanned = 0;
        boolean completed = false;

        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return TopKeysResponse.unavailable();

            ScanCursor cursor = ScanCursor.INITIAL;
            ScanArgs args = ScanArgs.Builder.limit(500);

            do {
                KeyScanCursor<byte[]> page = cmd.scan(cursor, args);

                for (byte[] keyBytes : page.getKeys()) {
                    keys.add(buildKeyInfo(cmd, keyBytes));
                    scanned++;
                }

                cursor = ScanCursor.of(page.getCursor());
                if (page.isFinished()) {
                    completed = true;
                    break;
                }

            } while (scanned < maxScan);

            logger.debug("Scanned {} keys for top-{} on {} (completed={})",
                    scanned, n, instance.getName(), completed);

        } catch (Exception e) {
            logger.error("Error fetching top N keys for {}: {}",
                    instance.getName(), e.getMessage());
            TopKeysResponse down = TopKeysResponse.unavailable();
            down.setStatus(failureStatus(e));
            return down;
        }

        keys.sort((a, b) -> Long.compare(b.getSizeBytes(), a.getSizeBytes()));
        List<KeyInfo> top = new ArrayList<>(keys.stream().limit(n).toList());
        return new TopKeysResponse(top, scanned, maxScan, completed);
    }

    // ═══════════════ Key TTL ═══════════════

    @Override
    public KeyTtlResponse getKeyTTL(InstanceRef instance, String key, String mapName) {
        // mapName Hazelcast'e özgüdür, Redis'te yok sayılır.
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return KeyTtlResponse.unavailable(key);

            Long pttl = cmd.pttl(key.getBytes(StandardCharsets.UTF_8));
            if (pttl == null) return KeyTtlResponse.unavailable(key);

            return new KeyTtlResponse(key, pttl);

        } catch (Exception e) {
            logger.error("Error fetching TTL on {}: {}", instance.getName(), e.getMessage());
            return KeyTtlResponse.unavailable(key);
        }
    }


    @Override
    public ReplicationInfo getReplicationInfo(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return ReplicationInfo.unavailable();

            String info = cmd.info("replication");
            String role = infoValue(info, "role", "unknown");

            ReplicationInfo r = new ReplicationInfo();
            r.setRole(role);
            r.setMasterReplOffset(infoLong(info, "master_repl_offset", 0));

            if ("slave".equalsIgnoreCase(role)) {
                r.setMasterLinkStatus(infoValue(info, "master_link_status", "unknown"));
                r.setMasterLastIoSecondsAgo(infoLong(info, "master_last_io_seconds_ago", -1));
                r.setMasterSyncInProgress(infoLong(info, "master_sync_in_progress", 0) == 1);

                long slaveOffset = infoLong(info, "slave_repl_offset", 0);
                long masterOffset = infoLong(info, "master_repl_offset", 0);
                r.setReplicaLagBytes(Math.max(0, masterOffset - slaveOffset));
                r.setOverallStatus(MetricStatus.AVAILABLE);
                return r;
            }

            int connected = (int) infoLong(info, "connected_slaves", 0);
            r.setConnectedReplicas(connected);
            r.setReplicas(parseReplicas(info, connected));

            // Standalone master (replica yok) → replication kullanılmıyor
            r.setOverallStatus(connected > 0
                    ? MetricStatus.AVAILABLE : MetricStatus.NOT_APPLICABLE);
            return r;

        } catch (Exception e) {
            logger.error("Error fetching replication info for {}: {}",
                    instance.getName(), e.getMessage());
            ReplicationInfo down = ReplicationInfo.unavailable();
            down.setOverallStatus(failureStatus(e));
            return down;
        }
    }

    /** "slave0:ip=127.0.0.1,port=6380,state=online,offset=1234,lag=0" */
    private List<ReplicationInfo.ReplicaInfo> parseReplicas(String info, int count) {
        List<ReplicationInfo.ReplicaInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String line = infoValue(info, "slave" + i, null);
            if (line == null) continue;

            String host = null, state = null;
            int port = 0;
            long offset = 0, lag = 0;

            for (String part : line.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                String k = kv[0].trim(), v = kv[1].trim();
                try {
                    switch (k) {
                        case "ip"     -> host = v;
                        case "port"   -> port = Integer.parseInt(v);
                        case "state"  -> state = v;
                        case "offset" -> offset = Long.parseLong(v);
                        case "lag"    -> lag = Long.parseLong(v);
                        default -> { }
                    }
                } catch (NumberFormatException ignored) { }
            }
            list.add(new ReplicationInfo.ReplicaInfo(host, port, state, offset, lag));
        }
        return list;
    }

    @Override
    public PersistenceInfo getPersistenceInfo(InstanceRef instance) {
        try {
            RedisCommands<byte[], byte[]> cmd = commandsFor(instance);
            if (cmd == null) return PersistenceInfo.unavailable();

            String info = cmd.info("persistence");

            PersistenceInfo p = new PersistenceInfo();
            p.setLoading(infoLong(info, "loading", 0) == 1);

            // RDB
            long lastSave = infoLong(info, "rdb_last_save_time", 0);
            long changes = infoLong(info, "rdb_changes_since_last_save", -1);
            boolean rdbActive = lastSave > 0 || changes >= 0;

            p.setRdbEnabled(rdbActive);
            p.setRdbBgsaveInProgress(infoLong(info, "rdb_bgsave_in_progress", 0) == 1);
            p.setRdbLastBgsaveStatus(infoValue(info, "rdb_last_bgsave_status", "unknown"));
            p.setRdbLastSaveTime(lastSave > 0 ? lastSave : null);
            p.setRdbChangesSinceLastSave(Math.max(0, changes));
            p.setRdbStatus(rdbActive ? MetricStatus.AVAILABLE : MetricStatus.NOT_APPLICABLE);

            // AOF
            boolean aofEnabled = infoLong(info, "aof_enabled", 0) == 1;
            p.setAofEnabled(aofEnabled);
            if (aofEnabled) {
                p.setAofRewriteInProgress(infoLong(info, "aof_rewrite_in_progress", 0) == 1);
                p.setAofLastBgrewriteStatus(infoValue(info, "aof_last_bgrewrite_status", "unknown"));
                p.setAofLastWriteStatus(infoValue(info, "aof_last_write_status", "unknown"));
                p.setAofStatus(MetricStatus.AVAILABLE);
            } else {
                p.setAofStatus(MetricStatus.NOT_APPLICABLE);
            }

            p.setOverallStatus((rdbActive || aofEnabled)
                    ? MetricStatus.AVAILABLE : MetricStatus.NOT_APPLICABLE);
            return p;

        } catch (Exception e) {
            logger.error("Error fetching persistence info for {}: {}",
                    instance.getName(), e.getMessage());
            PersistenceInfo down = PersistenceInfo.unavailable();
            down.setOverallStatus(failureStatus(e));
            return down;
        }
    }

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


    /** INFO keyspace satırlarının ayrıştırılmış özeti. */
    private record KeyspaceSummary(long totalKeys, long totalExpires,
                                   Long avgTtlMillis, Map<String, Long> perDatabase) {}

    /**
     * "db0:keys=1234,expires=100,avg_ttl=350000" satırlarını ayrıştırır.
     * Rehber §4.3 — DBSIZE yerine tüm database değerleri toplanır.
     */
    private KeyspaceSummary parseKeyspace(String keyspaceInfo) {
        long totalKeys = 0;
        long totalExpires = 0;
        double weightedTtlSum = 0;
        long ttlWeight = 0;
        Map<String, Long> perDb = new LinkedHashMap<>();

        if (keyspaceInfo == null) {
            return new KeyspaceSummary(0, 0, null, perDb);
        }

        for (String line : keyspaceInfo.split("\r?\n")) {
            line = line.trim();
            if (!line.startsWith("db")) continue;

            int colon = line.indexOf(':');
            if (colon < 0) continue;

            String dbName = line.substring(0, colon);
            String body = line.substring(colon + 1);

            long keys = 0, expires = 0, avgTtl = 0;
            for (String part : body.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length != 2) continue;
                try {
                    long value = Long.parseLong(kv[1].trim());
                    switch (kv[0].trim()) {
                        case "keys"    -> keys = value;
                        case "expires" -> expires = value;
                        case "avg_ttl" -> avgTtl = value;
                        default -> { }
                    }
                } catch (NumberFormatException ignored) { }
            }

            totalKeys += keys;
            totalExpires += expires;
            perDb.put(dbName, keys);

            // avg_ttl yalnızca expire tanımlı key'ler için anlamlı
            if (avgTtl > 0 && expires > 0) {
                weightedTtlSum += (double) avgTtl * expires;
                ttlWeight += expires;
            }
        }

        Long avgTtlMillis = (ttlWeight > 0)
                ? Math.round(weightedTtlSum / ttlWeight)
                : null;   // N/A

        return new KeyspaceSummary(totalKeys, totalExpires, avgTtlMillis, perDb);
    }

    private KeyInfo buildKeyInfo(RedisCommands<byte[], byte[]> cmd, byte[] keyBytes) {
        String keyName = new String(keyBytes, StandardCharsets.UTF_8);
        String type = cmd.type(keyBytes);

        Long pttl = cmd.pttl(keyBytes);
        long ttl = ( pttl != null) ? pttl : -2;

        long sizeBytes = safeMemoryUsage(cmd, keyBytes);

        KeyInfo ki = new KeyInfo(keyMasker.mask(keyName), type, sizeBytes, ttl);
        ki.setSizeStatus(MetricStatus.AVAILABLE);
        return ki;
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