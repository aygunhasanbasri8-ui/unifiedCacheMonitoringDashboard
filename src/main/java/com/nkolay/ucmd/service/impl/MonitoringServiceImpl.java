package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.*;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringServiceImpl implements MonitoringService {

    private final MonitorRegistry monitorRegistry;
    // In-memory storage (sonra veritabanı ile değiştirilecek)
    private Map<Long, InstanceRef> instances = new ConcurrentHashMap<>();
    private Long instanceIdCounter = 1L;

    @Autowired
    public MonitoringServiceImpl(MonitorRegistry monitorRegistry) {
        // Test data
        instances.put(1L, new InstanceRef(1L, "redis-prod", "REDIS", "localhost", 6379, true));
        instances.put(2L, new InstanceRef(2L, "hazelcast-cluster", "HAZELCAST", "localhost", 5701, true));
        this.monitorRegistry = monitorRegistry;
    }

    // ===== Instance CRUD =====
    @Override
    public List<InstanceRef> getAllInstances() {
        return new ArrayList<>(instances.values());
    }
    @Override
    public InstanceRef getInstanceById(Long id) {
        return instances.get(id);
    }
    @Override
    public InstanceRef createInstance(InstanceRef instance) {
        if (instance.getId() == null) {
            instance.setId(instanceIdCounter++);
        }
        instances.put(instance.getId(), instance);
        return instance;
    }

    @Override
    public InstanceRef updateInstance(InstanceRef instance) {
        if (instances.containsKey(instance.getId())) {
            instances.put(instance.getId(), instance);
            return instance;
        }
        return null;
    }

    @Override
    public void deleteInstance(Long id) {
        instances.remove(id);
    }


    // ===== Metrics =====
    @Override
    public HealthInfo getInstanceHealth(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getHealth(instance);
        } catch (Exception e) {
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getResourceStats(instance);
        } catch (Exception e) {
            return new ResourceStats(0,0,0);
        }
    }

    @Override
    public ConnectionStats getConnectionStats(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getConnectionStats(instance);
        } catch (Exception e) {
            return new ConnectionStats(0,0,0);
        }
        }

    @Override
    public DataStats getDataStats(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getDataStats(instance);
        } catch (Exception e) {
            return new DataStats(0,0,0);
        }
    }

    @Override
    public ClusterInfo getClusterInfo(Long id) {
        InstanceRef instance = instances.get(id);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getClusterInfo(instance);
        } catch (Exception e) {
            return null;
        }
    }
}
