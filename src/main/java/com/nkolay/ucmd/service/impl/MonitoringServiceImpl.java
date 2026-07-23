package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.repository.InstanceRepository;
import com.nkolay.ucmd.service.MonitoringService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MonitoringServiceImpl implements MonitoringService {

    private final MonitorRegistry monitorRegistry;
    private final InstanceRepository instanceRepository;
    private final KeyPatternConfig keyPatternConfig;


    public MonitoringServiceImpl(MonitorRegistry monitorRegistry, InstanceRepository instanceRepository, KeyPatternConfig keyPatternConfig) {
        this.monitorRegistry = monitorRegistry;
        this.instanceRepository = instanceRepository;
        this.keyPatternConfig = keyPatternConfig;

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

    @Override
    public CacheEfficiencyStats getCacheEfficiency(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return null;

        try {
            return monitorRegistry.getMonitor(instance).getCacheEfficiency(instance);
        } catch (Exception e) {
            return new CacheEfficiencyStats(0, 0);
        }
    }

    @Override
    public List<KeyPatternStats> getKeyPatternStats(Long id) {
        InstanceRef instance = instanceRepository.findById(id).orElse(null);
        if (instance == null) return new ArrayList<>();

        var patterns = keyPatternConfig.getKeyPatterns();
        System.out.println(">>> Yüklenen desen sayısı: " + patterns.size());
        patterns.forEach(p -> System.out.println(">>>   " + p.getPattern() + " → " + p.getLabel()));

        try {
            return monitorRegistry.getMonitor(instance)
                    .getKeyPatternStats(instance, keyPatternConfig.getKeyPatterns());
        } catch (Exception e) {
            return new ArrayList<>();
        }



    }

}
