package com.nkolay.ucmd.monitor;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class HazelcastMonitor implements CacheMonitor {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMonitor.class);
    private final HazelcastInstance hazelcastClient;
    private final HazelcastMetricsClient metricsClient;


    public HazelcastMonitor(HazelcastInstance hazelcastClient, HazelcastMetricsClient metricsClient) {
        this.hazelcastClient =  hazelcastClient;
        this.metricsClient = metricsClient;
    }

    @Override
    public String getSupportedType() {
        return "HAZELCAST";
    }

    @Override
    public boolean isReachable(InstanceRef instance) {
        try {
            return hazelcastClient.getLifecycleService().isRunning()
                    && !hazelcastClient.getCluster().getMembers().isEmpty();
        } catch (Exception e) {
            logger.warn("Hazelcast reachability check failed for {}: {}",
                    instance.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public HealthInfo getHealth(InstanceRef instance) {

        try {
            if (!isReachable(instance)) {
                return new HealthInfo("DOWN", "unknown", 0);
            }

            Cluster cluster = hazelcastClient.getCluster();
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
            List<Map<String, Double>> nodes = metricsClient.fetchAllNodes();

            if (nodes.isEmpty()) {
                logger.debug("Hazelcast metrics unavailable, returning zeros");
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

            logger.info(">>> HZ resources: {} node, heap {} bytes", nodes.size(), usedMemory);

            ResourceStats stats = new ResourceStats(cpuPercent, usedMemory, maxMemory);
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setCpuStats(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.AVAILABLE);
            stats.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching Hazelcast resource stats: {}", e.getMessage(), e);
            return new ResourceStats(0, 0, 0);
        }
    }

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            int memberCount = hazelcastClient
                    .getCluster().getMembers().size();
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
            long totalEntries = 0;
            for (String mapName : hazelcastClient.getDistributedObjects()
                    .stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .map(obj -> obj.getName())
                    .toList()) {
                totalEntries += hazelcastClient.getMap(mapName).size();

            }
            DataStats stats = new DataStats(totalEntries, 0, 0);
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.NOT_APPLICABLE);
            stats.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);
            return stats;


        } catch (Exception e) {
            DataStats down = new DataStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setMemoryStatus(MetricStatus.UNAVAILABLE);
            down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
            return down;
        }

    }


    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {
        try {
            Cluster cluster = hazelcastClient.getCluster();
            Set<Member> members = cluster.getMembers();

            ClusterInfo clusterInfo = new ClusterInfo(
                    members.size(), 271, hazelcastClient.getConfig().getClusterName()
            );
            clusterInfo.setOverallStatus(MetricStatus.AVAILABLE);
            clusterInfo.setBalancedStatus(MetricStatus.ESTIMATED);
            clusterInfo.setPartitionCountStatus(MetricStatus.ESTIMATED);
            clusterInfo.setBalanced(true);

            List<ClusterInfo.MemberInfo> memberInfoList = new ArrayList<>();
            boolean isFirst = true;
            for (Member member : members) {
                memberInfoList.add(new ClusterInfo.MemberInfo(
                        member.getUuid().toString(),
                        member.getAddress().getHost(),
                        member.getAddress().getPort(),
                        isFirst
                ));
                isFirst = false;
            }
            clusterInfo.setMembers(memberInfoList);
            return clusterInfo;
        } catch (Exception e) {
            logger.error("Error fetching cluster info: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor, int count) {

        List<KeyInfo> keys = new ArrayList<>();

        try {
            hazelcastClient.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .filter(obj -> prefix == null
                            || prefix.isEmpty()
                            || obj.getName().startsWith(prefix))
                    .limit(count)
                    .forEach(obj -> {
                        com.hazelcast.map.IMap<?, ?> map =
                                hazelcastClient.getMap(obj.getName());
                        KeyInfo ki = new KeyInfo(obj.getName(), "map", map.size(), -1);
                        ki.setSizeStatus(MetricStatus.ESTIMATED);
                        keys.add(ki);

                    });
        } catch (Exception e) {
            return new PageInfo<>(new ArrayList<>(), "0", false, count);
        }

        return new PageInfo<>(keys, "0", false, count);

    }

    @Override
    public List<KeyInfo> getTopNKeys(InstanceRef instance, int n) {
        List<KeyInfo> keys = new ArrayList<>();

        try {
            hazelcastClient.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .forEach(obj -> {
                        com.hazelcast.map.IMap<?, ?> map =
                                hazelcastClient.getMap(obj.getName());
                        KeyInfo ki = new KeyInfo(obj.getName(), "map", map.size(), -1);
                        ki.setSizeStatus(MetricStatus.ESTIMATED);
                        keys.add(ki);
                    });
        } catch (Exception e) {
            return new ArrayList<>();
        }

        keys.sort((a, b) ->
                Long.compare(b.getSizeBytes(), a.getSizeBytes()));
        return keys.stream().limit(n).toList();
    }

    @Override
    public long getKeyTTL(InstanceRef instance, String key) {
        try {
            boolean isMap = hazelcastClient.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                            .anyMatch(obj -> obj.getName().equals(key));
            return isMap ? -1 : -2;

        } catch (Exception e) {
            logger.error("Error fetching TTL for '{}' on {}: {}",
                    key, instance.getName(), e.getMessage(), e);
            return -2;
        }
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(InstanceRef instance) {
        CacheEfficiencyStats stats = new CacheEfficiencyStats(0, 0);
        stats.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return stats;
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
            hazelcastClient.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .forEach(obj -> {
                        String mapName = obj.getName();
                        long entryCount = hazelcastClient.getMap(mapName).size();

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
