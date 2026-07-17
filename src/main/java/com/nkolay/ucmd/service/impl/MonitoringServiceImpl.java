package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.*;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.repository.InstanceRepository;
import com.nkolay.ucmd.service.MonitoringService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MonitoringServiceImpl implements MonitoringService {

    private final MonitorRegistry monitorRegistry;

    private final InstanceRepository instanceRepository;

    public MonitoringServiceImpl(MonitorRegistry monitorRegistry, InstanceRepository instanceRepository) {
        this.monitorRegistry = monitorRegistry;
        this.instanceRepository = instanceRepository;
    }

    @PostConstruct
    public void initDefaultInstance () {
        if (instanceRepository.count() == 0){
            instanceRepository.save(new InstanceRef(null,"Redis-prod", "REDIS", "localhost", 6379, true));
            instanceRepository.save(new InstanceRef(null,"Hazelcast-cluster", "HAZELCAST", "localhost", 5701, true));
        }

    }

    // ===== Instance CRUD =====

    public List<InstanceRef> getAllInstances() {
        return instanceRepository.findAll();
    }

    public InstanceRef getInstanceById(Long id) {
        return instanceRepository.findById(id).orElse(null);
    }
    public InstanceRef createInstance(InstanceRef instance) {
        return instanceRepository.save(instance);
    }

    public InstanceRef updateInstance(InstanceRef instance) {
        if (instanceRepository.existsById(instance.getId())){
            return instanceRepository.save(instance);
        }
        return null;
    }

    public void deleteInstance(Long id) {
        instanceRepository.deleteById(id);
    }


    // ===== Metrics =====
    @Override
    public HealthInfo getInstanceHealth(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getHealth(instance);
        } catch (Exception e) {
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getResourceStats(instance);
        } catch (Exception e) {
            return new ResourceStats(0,0,0);
        }
    }

    @Override
    public ConnectionStats getConnectionStats(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getConnectionStats(instance);
        } catch (Exception e) {
            return new ConnectionStats(0,0,0);
        }
        }

    @Override
    public DataStats getDataStats(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getDataStats(instance);
        } catch (Exception e) {
            return new DataStats(0,0,0);
        }
    }

    @Override
    public ClusterInfo getClusterInfo(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getClusterInfo(instance);
        } catch (Exception e) {
            return null;
        }
    }
}
