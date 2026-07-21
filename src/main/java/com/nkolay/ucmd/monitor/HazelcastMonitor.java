package com.nkolay.ucmd.monitor;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Component
public class HazelcastMonitor implements CacheMonitor {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMonitor.class);

    private final HazelcastInstance hazelcastClient;

    public HazelcastMonitor(HazelcastInstance hazelcastClient) {
        this.hazelcastClient =  hazelcastClient;
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
        String version = hazelcastClient.getCluster().getMembers()
                .stream().findFirst().map(m -> m.getVersion()
                        .toString()).orElse("unknown");
        try {
            if (!isReachable(instance)) {
                return new HealthInfo("DOWN", "unknown", 0);
            }

            Cluster cluster = hazelcastClient.getCluster();
            long clusterUptime = System.currentTimeMillis()
                    - cluster.getClusterTime();

            return new HealthInfo("UP", "5.3.6", clusterUptime / 1000);

        } catch (Exception e) {
            logger.error("Error fetching Hazelcast health: {}", e.getMessage(), e);
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(InstanceRef instance) {
        try {

            int memberCount = hazelcastClient.getCluster().getMembers().size();

            long estimatedMemory = memberCount * 512L * 1024 * 1024;
            long usedMemory = (long) (estimatedMemory * 0.5);

            return new ResourceStats(0.0, usedMemory, estimatedMemory);
        } catch (Exception e) {
            logger.error("Error fetching resource stats: {}", e.getMessage(), e);
            return new ResourceStats(0,0,0);
        }
    }

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            int memberCount = hazelcastClient
                    .getCluster().getMembers().size();
            return new ConnectionStats(memberCount, 0, 0);
        } catch (Exception e) {
            return new ConnectionStats(0, 0, 0);
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
            return new DataStats(totalEntries, 0, 0);


        } catch (Exception e) {
            return new DataStats(0,0,0);
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
                        keys.add(new KeyInfo(
                                obj.getName(),
                                "map",
                                map.size(),
                                -1
                        ));
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
                        keys.add(new KeyInfo(
                                obj.getName(),
                                "map",
                                map.size(),
                                -1
                        ));
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
        return new CacheEfficiencyStats(0, 0);
    }


}
