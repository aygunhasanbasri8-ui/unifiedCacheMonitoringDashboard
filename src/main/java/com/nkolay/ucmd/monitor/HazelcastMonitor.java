package com.nkolay.ucmd.monitor;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.partition.Partition;
import com.hazelcast.partition.PartitionService;
import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.security.KeyMasker;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class HazelcastMonitor implements CacheMonitor {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMonitor.class);
    private final HazelcastConnectionManager connectionManager;
    private final HazelcastMetricsClient metricsClient;
    private final KeyMasker keyMasker;


    public HazelcastMonitor(HazelcastConnectionManager connectionManager,
                            HazelcastMetricsClient metricsClient,
                            KeyMasker keyMasker) {
        this.connectionManager = connectionManager;
        this.metricsClient = metricsClient;
        this.keyMasker = keyMasker;
    }

    @Override
    public String getSupportedType() {
        return "HAZELCAST";
    }

    @Override
    public boolean isReachable(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) return false;
            return client.getLifecycleService().isRunning()
                    && !client.getCluster().getMembers().isEmpty();
        } catch (Exception e) {
            logger.warn("Hazelcast reachability check failed for {}: {}",
                    instance.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public HealthInfo getHealth(InstanceRef instance) {

        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null || !isReachable(instance)) {
                return new HealthInfo("DOWN", "unknown", 0);
            }

            Cluster cluster = client.getCluster();
            long clusterUptime = System.currentTimeMillis()
                    - cluster.getClusterTime();

            String version = cluster.getMembers().stream()
                    .findFirst()
                    .map(m -> m.getVersion().toString())
                    .orElse("unknown");
            return new HealthInfo("UP", version, clusterUptime / 1000);

        } catch (Exception e) {
            logger.error("Error fetching Hazelcast health: {}", e.getMessage(), e);
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(InstanceRef instance) {
        try {
            List<Map<String, Double>> nodes = metricsClient.fetchAllNodes(instance);

            if (nodes.isEmpty()) {
                logger.debug("Hazelcast metrics unavailable for {}", instance.getName());
                ResourceStats down = new ResourceStats(0,0,0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setCpuStats(MetricStatus.UNAVAILABLE);
                down.setMemoryStatus(MetricStatus.UNAVAILABLE);
                down.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);

                return down;
            }

            // Cluster toplam heap kullanımı (tüm node'lar toplanır)
            long usedMemory = (long) metricsClient.sumAcrossNodes(
                    nodes, "jvm_memory_bytes_used", "area=\"heap\"");
            long maxMemory = (long) metricsClient.sumAcrossNodes(
                    nodes, "jvm_memory_bytes_max", "area=\"heap\"");

            // CPU: node ortalaması (oransal değer, toplanmaz)
            double cpuPercent = metricsClient.avgAcrossNodes(
                    nodes, "com_hazelcast_metrics_processcpuload", null) * 100;
            cpuPercent = Math.round(cpuPercent * 100.0) / 100.0;

            logger.debug("HZ resources: {} node, heap {} bytes", nodes.size(), usedMemory);

            ResourceStats stats = new ResourceStats(cpuPercent, usedMemory, maxMemory);
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setCpuStats(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.AVAILABLE);
            stats.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching Hazelcast resource stats: {}", e.getMessage(), e);
            ResourceStats down = new ResourceStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setCpuStats(MetricStatus.UNAVAILABLE);
            down.setMemoryStatus(MetricStatus.UNAVAILABLE);
            down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                ConnectionStats down = new ConnectionStats(0, 0, 0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setActiveClientStatus(MetricStatus.UNAVAILABLE);
                down.setErrorStatus(MetricStatus.UNAVAILABLE);
                return down;
            }
            int memberCount = client.getCluster().getMembers().size();
            ConnectionStats stats = new ConnectionStats(memberCount, 0, 0);
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setActiveClientStatus(MetricStatus.ESTIMATED);
            stats.setErrorStatus(MetricStatus.AVAILABLE);
            return stats;
        } catch (Exception e) {
            ConnectionStats down = new ConnectionStats(0, 0, 0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setActiveClientStatus(MetricStatus.UNAVAILABLE);
            down.setErrorStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }


    @Override
    public DataStats getDataStats(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                DataStats down = new DataStats(0, 0, 0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setMemoryStatus(MetricStatus.UNAVAILABLE);
                down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
                down.setEvictionStatus(MetricStatus.UNAVAILABLE);
                down.setTtlStatus(MetricStatus.UNAVAILABLE);
                return down;
            }

            long totalEntries = 0;
            for (String mapName : client.getDistributedObjects()
                    .stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .map(obj -> obj.getName())
                    .toList()) {
                totalEntries += client.getMap(mapName).size();

            }
            DataStats stats = new DataStats(totalEntries, 0, 0);
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.NOT_APPLICABLE);
            stats.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);
            stats.setEvictionStatus(MetricStatus.NOT_APPLICABLE);
            stats.setTtlStatus(MetricStatus.NOT_APPLICABLE);
            return stats;


        } catch (Exception e) {
            DataStats down = new DataStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setMemoryStatus(MetricStatus.UNAVAILABLE);
            down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
            down.setEvictionStatus(MetricStatus.UNAVAILABLE);
            down.setTtlStatus(MetricStatus.UNAVAILABLE);
            return down;
        }

    }


    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null || !isReachable(instance)) {
                ClusterInfo down = new ClusterInfo();
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
                down.setDistributionStatus(MetricStatus.UNAVAILABLE);
                down.setSafetyStatus(MetricStatus.UNAVAILABLE);
                return down;
            }

            Cluster cluster = client.getCluster();
            Set<Member> members = cluster.getMembers();

            ClusterInfo info = new ClusterInfo();
            info.setNodeCount(members.size());
            info.setClusterName(client.getConfig().getClusterName());
            info.setOverallStatus(MetricStatus.AVAILABLE);

            // Hazelcast üye setini yaşa göre sıralar — ilk üye master'dır
            List<ClusterInfo.MemberInfo> memberInfoList = new ArrayList<>();
            boolean isFirst = true;
            for (Member member : members) {
                memberInfoList.add(new ClusterInfo.MemberInfo(
                        member.getUuid().toString(),
                        member.getAddress().getHost(),
                        member.getAddress().getPort(),
                        isFirst));
                isFirst = false;
            }
            info.setMembers(memberInfoList);

            applyPartitionMetrics(client, info);

            try {
                PartitionService ps = client.getPartitionService();
                info.setClusterSafe(ps.isClusterSafe());
                info.setSafetyStatus(MetricStatus.AVAILABLE);
            } catch (UnsupportedOperationException e) {
                info.setClusterSafe(null);
                info.setSafetyStatus(MetricStatus.NOT_APPLICABLE);
            } catch (Exception e) {
                info.setClusterSafe(null);
                info.setSafetyStatus(MetricStatus.UNAVAILABLE);
            }

            return info;

        } catch (Exception e) {
            logger.error("Error fetching cluster info: {}", e.getMessage());
            ClusterInfo down = new ClusterInfo();
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
            down.setDistributionStatus(MetricStatus.UNAVAILABLE);
            down.setSafetyStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }

    /** Rehber §5.4 — gerçek partition sayısı, member başına dağılım ve skew. */
    private void applyPartitionMetrics(HazelcastInstance client, ClusterInfo info) {
        try {
            PartitionService ps = client.getPartitionService();
            Set<Partition> partitions = ps.getPartitions();

            if (partitions == null || partitions.isEmpty()) {
                info.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
                info.setDistributionStatus(MetricStatus.UNAVAILABLE);
                return;
            }

            info.setPartitionCount(partitions.size());
            info.setPartitionCountStatus(MetricStatus.AVAILABLE);

            Map<String, Integer> distribution = new LinkedHashMap<>();
            int unassigned = 0;
            for (Partition p : partitions) {
                Member owner = p.getOwner();
                if (owner == null) { unassigned++; continue; }
                String key = owner.getAddress().getHost() + ":" + owner.getAddress().getPort();
                distribution.merge(key, 1, Integer::sum);
            }

            info.setPartitionDistribution(distribution);
            info.setUnassignedPartitions(unassigned);

            if (distribution.isEmpty()) {
                info.setDistributionStatus(MetricStatus.UNAVAILABLE);
                info.setBalanced(null);
                return;
            }

            int max = distribution.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = distribution.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            double avg = (double) partitions.size() / distribution.size();
            double skew = (avg > 0) ? ((max - min) / avg) * 100.0 : 0.0;

            info.setDistributionSkewPercent(Math.round(skew * 100.0) / 100.0);
            info.setBalanced(skew <= 10.0 && unassigned == 0);
            info.setDistributionStatus(unassigned > 0
                    ? MetricStatus.ESTIMATED : MetricStatus.AVAILABLE);
            info.setBalancedStatus(MetricStatus.AVAILABLE);

        } catch (UnsupportedOperationException e) {
            info.setPartitionCountStatus(MetricStatus.NOT_APPLICABLE);
            info.setDistributionStatus(MetricStatus.NOT_APPLICABLE);
            info.setBalanced(null);
        } catch (Exception e) {
            logger.warn("Partition metrics unavailable: {}", e.getMessage());
            info.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
            info.setDistributionStatus(MetricStatus.UNAVAILABLE);
            info.setBalanced(null);
        }
    }

    @Override
    public MapStatsInfo getMapStats(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                return MapStatsInfo.unavailable();
            }

            MapStatsInfo result = new MapStatsInfo();
            List<MapStatsInfo.MapDetail> details = new ArrayList<>();
            long totalEntries = 0;

            for (var obj : client.getDistributedObjects()) {
                if (!(obj instanceof com.hazelcast.map.IMap)) continue;
                String mapName = obj.getName();
                long size = client.getMap(mapName).size();
                totalEntries += size;
                details.add(new MapStatsInfo.MapDetail(keyMasker.mask(mapName), size));
            }

            result.setMapCount(details.size());
            result.setTotalEntryCount(totalEntries);
            result.setMaps(details);
            result.setOverallStatus(MetricStatus.AVAILABLE);
            // LocalMapStats client-only modda desteklenmez (rehber §5.3).
            result.setMemoryCostStatus(MetricStatus.NOT_APPLICABLE);
            result.setOperationStatus(MetricStatus.NOT_APPLICABLE);
            return result;

        } catch (Exception e) {
            logger.error("Error fetching map stats for {}: {}",
                    instance.getName(), e.getMessage());
            return MapStatsInfo.unavailable();
        }
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor, int count, MatchMode matchMode) {

        List<KeyInfo> keys = new ArrayList<>();
        final String search = (prefix == null) ? "" : prefix.trim();
        final MatchMode mode = (matchMode == null ) ? MatchMode.STARTS_WITH : matchMode;

        final HazelcastInstance client = connectionManager.getClient(instance);
        if (client == null) {
            return PageInfo.unavailable(count);
        }

        try {
            client.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .filter(obj -> search.isEmpty() || (mode == MatchMode.CONTAINS)
                            ? obj.getName().contains(search)
                            : obj.getName().startsWith(search) )
                    .limit(count)
                    .forEach(obj -> {
                        com.hazelcast.map.IMap<?, ?> map =
                                client.getMap(obj.getName());
                        KeyInfo ki = new KeyInfo(keyMasker.mask(obj.getName()), "map", 0, -1);
                        ki.setEntryCount((long) map.size());
                        ki.setSizeStatus(MetricStatus.NOT_APPLICABLE);   // byte boyutu alınamaz
                        keys.add(ki);

                    });
        } catch (Exception e) {
            logger.error("Error scanning maps for instance {}: {}", instance.getName(), e.getMessage());
            return PageInfo.unavailable(count);
        }

        return new PageInfo<>(keys, "0", false, count);

    }

    @Override
    public TopKeysResponse getTopNKeys(InstanceRef instance, int n, Integer scanLimit) {
        List<KeyInfo> keys = new ArrayList<>();
        int scanned = 0;

        HazelcastInstance client = connectionManager.getClient(instance);
        if (client == null) {
            return TopKeysResponse.unavailable();
        }

        try {
            for (var obj : client.getDistributedObjects()) {
                if (!(obj instanceof com.hazelcast.map.IMap)) continue;
                com.hazelcast.map.IMap<?, ?> map = client.getMap(obj.getName());
                KeyInfo ki = new KeyInfo(keyMasker.mask(obj.getName()), "map", 0, -1);
                ki.setEntryCount((long) map.size());
                ki.setSizeStatus(MetricStatus.NOT_APPLICABLE);   // byte boyutu alınamaz
                keys.add(ki);
                scanned++;
            }
        } catch (Exception e) {
            logger.error("Error fetching top N maps for {}: {}", instance.getName(), e.getMessage());
            return TopKeysResponse.unavailable();
        }

        keys.sort((a, b) -> Long.compare(
                b.getEntryCount() != null ? b.getEntryCount() : 0,
                a.getEntryCount() != null ? a.getEntryCount() : 0));
        List<KeyInfo> top = new ArrayList<>(keys.stream().limit(n).toList());
        TopKeysResponse response = new TopKeysResponse(top, scanned, scanned, true);
        response.setEstimated(true);
        response.setStatus(MetricStatus.ESTIMATED);

        return response;
    }

    @Override
    public KeyTtlResponse getKeyTTL(InstanceRef instance, String key) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                return KeyTtlResponse.unavailable(key);
            }
            boolean isMap = client.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .anyMatch(obj -> obj.getName().equals(key));
            return isMap ? new KeyTtlResponse(key, -1, TtlStatus.PERSISTENT) : new KeyTtlResponse(key, -2, TtlStatus.NOT_FOUND);

        } catch (Exception e) {
            logger.error("Error fetching TTL for '{}' on {}: {}",
                    keyMasker.maskForLog(key), instance.getName(), e.getMessage());
            return KeyTtlResponse.unavailable(key);
        }
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(InstanceRef instance) {
        CacheEfficiencyStats stats = new CacheEfficiencyStats(0, 0);
        stats.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return stats;
    }

    @Override
    public ReplicationInfo getReplicationInfo(InstanceRef instance) {
        return ReplicationInfo.notApplicable();
    }

    /** Hazelcast persistence (Hot Restart) enterprise özelliğidir. */
    @Override
    public PersistenceInfo getPersistenceInfo(InstanceRef instance) {
        return PersistenceInfo.notApplicable();
    }

    @Override
    public List<KeyPatternStats> getKeyPatternStats(InstanceRef instance,
                                                    List<KeyPatternConfig.PatternDef> patterns) {
        // Hazelcast'te "key" = IMap adı. Map'leri desenlere göre grupla.
        List<KeyPatternStats> result = new ArrayList<>();
        for (KeyPatternConfig.PatternDef def : patterns) {
            result.add(new KeyPatternStats(def.getPattern(), def.getLabel()));
        }
        KeyPatternStats other = new KeyPatternStats("*", "Diğer");

        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                result.removeIf(s -> s.getKeyCount() == 0);
                return result;
            }

            client.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .forEach(obj -> {
                        String mapName = obj.getName();
                        long entryCount = client.getMap(mapName).size();

                        boolean matched = false;
                        for (KeyPatternStats stats : result) {
                            if (matchesPatternHz(mapName, stats.getPattern())) {
                                stats.increment(entryCount);   // boyut yerine entry sayısı
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
                            other.increment(entryCount);
                        }
                    });
        } catch (Exception e) {
            logger.error("Error scanning key patterns for {}: {}",
                    instance.getName(), e.getMessage(), e);
        }

        if (other.getKeyCount() > 0) {
            result.add(other);
        }
        result.removeIf(s -> s.getKeyCount() == 0);
        return result;
    }

    private boolean matchesPatternHz(String mapName, String pattern) {

        return matchesPattern(mapName, pattern);
    }

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
