package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.exception.InstanceNotFoundException;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.CacheMonitor;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.repository.InstanceRepository;
import com.nkolay.ucmd.service.MonitoringService;
import org.hibernate.Cache;
import org.hibernate.grammars.hql.HqlParser;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MonitoringServiceImpl implements MonitoringService {


    private final Logger logger = LoggerFactory.getLogger(MonitoringServiceImpl.class);

    private final MonitorRegistry monitorRegistry;
    private final InstanceRepository instanceRepository;
    private final KeyPatternConfig keyPatternConfig;


    public MonitoringServiceImpl(MonitorRegistry monitorRegistry, InstanceRepository instanceRepository, KeyPatternConfig keyPatternConfig) {
        this.monitorRegistry = monitorRegistry;
        this.instanceRepository = instanceRepository;
        this.keyPatternConfig = keyPatternConfig;

    }

    @PostConstruct
    public void initDefaultInstance() {
        if (instanceRepository.count() == 0) {
            instanceRepository.save(new InstanceRef(null, "Redis-prod", "REDIS", "localhost", 6379, true));
            instanceRepository.save(new InstanceRef(null, "Hazelcast-cluster", "HAZELCAST", "localhost", 5701, true));
            instanceRepository.save(new InstanceRef(null, "Redis-cache", "REDIS", "localhost", 6380, true));
        }
    }

    // ===== Instance CRUD =====
    @Override
    public List<InstanceRef> getAllInstances() {
        return instanceRepository.findAll();
    }

    @Override
    public InstanceRef getInstanceById(Long id) {
        return instanceRepository.findById(id).orElseThrow(() -> new InstanceNotFoundException(id));
    }

    @Override
    public InstanceRef createInstance(InstanceRef instance) {
        return instanceRepository.save(instance);
    }

    @Override
    public InstanceRef updateInstance(InstanceRef instance) {
        if (instanceRepository.existsById(instance.getId())){
            return instanceRepository.save(instance);
        }
        return null;
    }

    @Override
    public void deleteInstance(Long id) {
        instanceRepository.deleteById(id);
    }


    /// ===== Metrics =====

    @Override
    public HealthInfo getInstanceHealth(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);
        try {
            HealthInfo health = monitor.getHealth(instance);
            return health != null ? health : new HealthInfo("DOWN", "unknown", 0);
        } catch (Exception e) {
            logger.warn("Health check failed for {}: {}", instance.getName(), e.getMessage() );
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        try {
            ResourceStats stats = monitor.getResourceStats(instance);
            return stats != null ? stats : unavailableResourceStats();
        } catch (Exception e) {
            logger.warn("Resource check failed for {}: {}", instance.getName(), e.getMessage());
            return unavailableResourceStats();
        }
    }

    @Override
    public ConnectionStats getConnectionStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        try {
            ConnectionStats stats = monitor.getConnectionStats(instance);
            return stats != null ? stats : unavailableConnectionStats();
        } catch (Exception e) {
            logger.warn("Connection stats check failed for {}: {}", instance.getName(), e.getMessage());
            return unavailableConnectionStats();
        }
    }

    @Override
    public DataStats getDataStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        try {
            DataStats stats = monitor.getDataStats(instance);
            return stats != null ? stats : unavailableDataStats();
        } catch (Exception e) {
            logger.warn("Data Stats check failed for {}: {}", instance.getName(), e.getMessage());
            return unavailableDataStats();
        }
    }

    @Override
    public ClusterInfo getClusterInfo(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        try {
            ClusterInfo info = monitor.getClusterInfo(instance);
            return info != null ? info : unavailableClusterInfo();
        } catch (Exception e) {
            logger.warn("Cluster info check failed for {}: {}", instance.getName(), e.getMessage());
            return unavailableClusterInfo();
        }
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        try {
            CacheEfficiencyStats stats = monitor.getCacheEfficiency(instance);
            return stats != null ? stats : unavailableCacheEfficiencyStats();
        } catch (Exception e) {
            logger.warn("Cache Efficiency Stats check failed for {}: {}", instance.getName(), e.getMessage());
            return unavailableCacheEfficiencyStats();
        }
    }

    @Override
    public List<KeyPatternStats> getKeyPatternStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        try {
            List<KeyPatternStats> stats = monitor.getKeyPatternStats(instance, keyPatternConfig.getKeyPatterns());
            return stats != null ? stats : new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Key Patterr Stats check failed for {}{}", instance.getName(), e.getMessage());
            return new ArrayList<>();
        }



    }

    ///=== Unavailable DTO ===

    private ResourceStats unavailableResourceStats() {
        ResourceStats s = new ResourceStats(0,0,0);
        s.setOverallStatus(MetricStatus.UNAVAILABLE);
        s.setFragmentationStatus(MetricStatus.UNAVAILABLE);
        s.setCpuStats(MetricStatus.UNAVAILABLE);
        s.setMemoryStatus(MetricStatus.UNAVAILABLE);

        return s;
    }

    private ConnectionStats unavailableConnectionStats() {
        ConnectionStats s = new ConnectionStats(0,0,0);
        s.setOverallStatus(MetricStatus.UNAVAILABLE);
        s.setActiveClientStatus(MetricStatus.UNAVAILABLE);
        s.setErrorStatus(MetricStatus.UNAVAILABLE);
        return s;
    }

    private DataStats unavailableDataStats() {
        DataStats s = new DataStats(0,0,0);
        s.setOverallStatus(MetricStatus.UNAVAILABLE);
        s.setFragmentationStatus(MetricStatus.UNAVAILABLE);
        s.setMemoryStatus(MetricStatus.UNAVAILABLE);
        return s;
    }

    private ClusterInfo unavailableClusterInfo() {
        ClusterInfo s = new ClusterInfo();
        s.setOverallStatus(MetricStatus.UNAVAILABLE);
        s.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
        s.setBalancedStatus(MetricStatus.UNAVAILABLE);
        return s;
    }

    private CacheEfficiencyStats unavailableCacheEfficiencyStats() {
        CacheEfficiencyStats s = new CacheEfficiencyStats();
        s.setOverallStatus(MetricStatus.UNAVAILABLE);
        return s;
    }

}
