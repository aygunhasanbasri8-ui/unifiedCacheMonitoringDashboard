package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.*;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringService {

    // In-memory storage (sonra veritabanı ile değiştirilecek)
    private Map<Long, InstanceRef> instances = new ConcurrentHashMap<>();
    private Long instanceIdCounter = 1L;

    public MonitoringService() {
        // Test data
        instances.put(1L, new InstanceRef(1L, "redis-prod", "REDIS", "localhost", 6379, true));
        instances.put(2L, new InstanceRef(2L, "hazelcast-cluster", "HAZELCAST", "localhost", 5701, true));
    }

    // ===== Instance CRUD =====
    public List<InstanceRef> getAllInstances() {
        return new ArrayList<>(instances.values());
    }

    public InstanceRef getInstanceById(Long id) {
        return instances.get(id);
    }

    public InstanceRef createInstance(InstanceRef instance) {
        if (instance.getId() == null) {
            instance.setId(instanceIdCounter++);
        }
        instances.put(instance.getId(), instance);
        return instance;
    }

    public InstanceRef updateInstance(InstanceRef instance) {
        if (instances.containsKey(instance.getId())) {
            instances.put(instance.getId(), instance);
            return instance;
        }
        return null;
    }

    public void deleteInstance(Long id) {
        instances.remove(id);
    }

    // ===== Metrics =====
    public HealthInfo getInstanceHealth(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        // Mock data
        if ("REDIS".equals(instance.getType())) {
            return new HealthInfo("UP", "7.0.0", 3600);
        } else {
            return new HealthInfo("UP", "5.3.4", 7200);
        }
    }

    public ResourceStats getResourceStats(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        // Mock data
        ResourceStats stats = new ResourceStats(25.5, 512000000L, 1024000000L);
        stats.setFragmentationRatio(1.05);
        return stats;
    }

    public ConnectionStats getConnectionStats(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        // Mock data
        return new ConnectionStats(45, 2, 0);
    }

    public DataStats getDataStats(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        // Mock data
        DataStats stats = new DataStats(15000, 512000000L, 1024000000L);
        stats.setFragmentationRatio(1.08);
        return stats;
    }

    public ClusterInfo getClusterInfo(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        if ("HAZELCAST".equals(instance.getType())) {
            ClusterInfo clusterInfo = new ClusterInfo(3, 271, "default");
            clusterInfo.setBalanced(true);

            // Mock members
            clusterInfo.getMembers().add(
                    new ClusterInfo.MemberInfo("member-1", "192.168.1.10", 5701, true)
            );
            clusterInfo.getMembers().add(
                    new ClusterInfo.MemberInfo("member-2", "192.168.1.11", 5701, false)
            );
            clusterInfo.getMembers().add(
                    new ClusterInfo.MemberInfo("member-3", "192.168.1.12", 5701, false)
            );

            return clusterInfo;
        }
        return null;
    }
}
