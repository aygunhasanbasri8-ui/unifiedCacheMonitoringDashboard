package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.exception.InstanceNotFoundException;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.CacheMonitor;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.monitor.ProtectedMonitorExecutor;
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
    private final ProtectedMonitorExecutor executor;


    public MonitoringServiceImpl(MonitorRegistry monitorRegistry, InstanceRepository instanceRepository, KeyPatternConfig keyPatternConfig, ProtectedMonitorExecutor executor) {
        this.monitorRegistry = monitorRegistry;
        this.instanceRepository = instanceRepository;
        this.keyPatternConfig = keyPatternConfig;
        this.executor = executor;

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
        if (instance.getId() == null || !instanceRepository.existsById(instance.getId())) {
            throw new InstanceNotFoundException(instance.getId());
        }
        InstanceRef saved = instanceRepository.save(instance);
        executor.invalidate(saved);
        return saved;
    }

    @Override
    public void deleteInstance(Long id) {
        instanceRepository.findById(id).ifPresent(executor::invalidate);
        instanceRepository.deleteById(id);
    }


    /// ===== Metrics =====

    @Override
    public HealthInfo getInstanceHealth(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "health",
                () -> monitor.getHealth(instance));

        if (result.isUnavailable() || result.value() == null) {
            return new HealthInfo("DOWN", "unknown", 0);
        }
        if (result.isStale()) {
            HealthInfo stale = result.value();
            // Bayat sağlık bilgisi UP gösterilmemeli
            return new HealthInfo("DOWN", stale.getVersion(), stale.getUptimeSeconds());
        }
        return result.value();
    }

    @Override
    public ResourceStats getResourceStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "resources",
                () -> monitor.getResourceStats(instance));

        if (result.isUnavailable() || result.value() == null) {
            return unavailableResourceStats();
        }
        ResourceStats stats = result.value();
        if (result.isStale()) {
            stats.setOverallStatus(MetricStatus.STALE);
        }
        return stats;
    }

    @Override
    public ConnectionStats getConnectionStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "connections",
                () -> monitor.getConnectionStats(instance));

        if (result.isUnavailable() || result.value() == null) {
            return unavailableConnectionStats();
        }
        ConnectionStats stats = result.value();
        if (result.isStale()) {
            stats.setOverallStatus(MetricStatus.STALE);
        }
        return stats;
    }

    @Override
    public DataStats getDataStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "data-stats",
                () -> monitor.getDataStats(instance));

        if (result.isUnavailable() || result.value() == null) {
            return unavailableDataStats();
        }
        DataStats stats = result.value();
        if (result.isStale()) {
            stats.setOverallStatus(MetricStatus.STALE);
        }
        return stats;
    }

    @Override
    public ClusterInfo getClusterInfo(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "cluster",
                () -> monitor.getClusterInfo(instance));

        if (result.isUnavailable() || result.value() == null) {
            return unavailableClusterInfo();
        }
        ClusterInfo info = result.value();
        if (result.isStale()) {
            info.setOverallStatus(MetricStatus.STALE);
        }
        return info;
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "efficiency",
                () -> monitor.getCacheEfficiency(instance));

        if (result.isUnavailable() || result.value() == null) {
            return unavailableCacheEfficiencyStats();
        }
        CacheEfficiencyStats stats = result.value();
        if (result.isStale()) {
            stats.setOverallStatus(MetricStatus.STALE);
        }
        return stats;
    }

    /** SCAN tabanlı — pahalı işlem, tüm korumalar aktif, cache yok. */
    @Override
    public List<KeyPatternStats> getKeyPatternStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        List<KeyPatternStats> stats = executor.executeExpensive(instance, "key-patterns",
                () -> monitor.getKeyPatternStats(instance, keyPatternConfig.getKeyPatterns()));

        return stats != null ? stats : new ArrayList<>();
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
