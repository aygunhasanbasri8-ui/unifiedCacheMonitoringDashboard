package com.ucmd.service.impl;

import com.ucmd.config.KeyPatternConfig;
import com.ucmd.config.SeedInstanceConfig;
import com.ucmd.enums.MetricStatus;
import com.ucmd.exception.InstanceNotFoundException;
import com.ucmd.model.dto.*;
import com.ucmd.model.entity.InstanceRef;
import com.ucmd.monitor.CacheMonitor;
import com.ucmd.monitor.HazelcastConnectionManager;
import com.ucmd.monitor.MonitorRegistry;
import com.ucmd.monitor.ProtectedMonitorExecutor;
import com.ucmd.repository.InstanceRepository;
import com.ucmd.service.MonitoringService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

@Service
public class MonitoringServiceImpl implements MonitoringService {

    private final MonitorRegistry monitorRegistry;
    private final InstanceRepository instanceRepository;
    private final KeyPatternConfig keyPatternConfig;
    private final ProtectedMonitorExecutor executor;
    private final HazelcastConnectionManager hazelcastConnectionManager;
    private final SeedInstanceConfig seedInstanceConfig;

    public MonitoringServiceImpl(MonitorRegistry monitorRegistry, InstanceRepository instanceRepository, KeyPatternConfig keyPatternConfig, ProtectedMonitorExecutor executor, HazelcastConnectionManager hazelcastConnectionManager, SeedInstanceConfig seedInstanceConfig) {
        this.monitorRegistry = monitorRegistry;
        this.instanceRepository = instanceRepository;
        this.keyPatternConfig = keyPatternConfig;
        this.executor = executor;
        this.hazelcastConnectionManager = hazelcastConnectionManager;
        this.seedInstanceConfig = seedInstanceConfig;
    }

    @PostConstruct
    public void initDefaultInstance() {
        if (instanceRepository.count() > 0) return;

        seedInstanceConfig.getInstances().stream()
                .map(SeedInstanceConfig.SeedInstance::toInstanceRef)
                .forEach(instanceRepository::save);
    }

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
        // Eski yapılandırmayla açılmış Hazelcast client'ı kapat
        instanceRepository.findById(instance.getId())
                .ifPresent(hazelcastConnectionManager::evict);

        InstanceRef saved = instanceRepository.save(instance);
        executor.invalidate(saved);
        hazelcastConnectionManager.evict(saved);
        return saved;
    }

    @Override
    public void deleteInstance(Long id) {
        instanceRepository.findById(id).ifPresent(existing -> {
            executor.invalidate(existing);
            hazelcastConnectionManager.evict(existing);
        });
        instanceRepository.deleteById(id);
    }

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
            // Bayat veri varken UP göstermemeye dikkat et
            HealthInfo down = new HealthInfo("DOWN", stale.getVersion(), stale.getUptimeSeconds());
            down.setMeta(result.toMeta());
            return down;
        }
        HealthInfo health = result.value();
        health.setMeta(result.toMeta());
        return health;
    }

    @Override
    public ResourceStats getResourceStats(Long id) {
        return collect(id, "resources", CacheMonitor::getResourceStats,
                this::unavailableResourceStats, true);
    }

    @Override
    public ConnectionStats getConnectionStats(Long id) {
        return collect(id, "connections", CacheMonitor::getConnectionStats,
                this::unavailableConnectionStats, false);
    }

    @Override
    public DataStats getDataStats(Long id) {
        return collect(id, "data-stats", CacheMonitor::getDataStats,
                this::unavailableDataStats, false);
    }

    @Override
    public ClusterInfo getClusterInfo(Long id) {
        return collect(id, "cluster", CacheMonitor::getClusterInfo,
                this::unavailableClusterInfo, false);
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(Long id) {
        return collect(id, "efficiency", CacheMonitor::getCacheEfficiency,
                this::unavailableCacheEfficiencyStats, false);
    }

    @Override
    public ReplicationInfo getReplicationInfo(Long id) {
        return collect(id, "replication", CacheMonitor::getReplicationInfo,
                ReplicationInfo::unavailable, true);
    }

    @Override
    public PersistenceInfo getPersistenceInfo(Long id) {
        return collect(id, "persistence", CacheMonitor::getPersistenceInfo,
                PersistenceInfo::unavailable, true);
    }

    @Override
    public MapStatsInfo getMapStats(Long id) {
        return collect(id, "map-stats", CacheMonitor::getMapStats,
                MapStatsInfo::unavailable, true);
    }

    @Override
    public OperationStatsInfo getOperationStats(Long id) {
        return collect(id, "operation-stats", CacheMonitor::getOperationStats,
                OperationStatsInfo::unavailable, true);
    }

    @Override
    public List<KeyPatternStats> getKeyPatternStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        List<KeyPatternStats> stats = executor.executeExpensive(instance, "key-patterns",
                () -> monitor.getKeyPatternStats(instance, keyPatternConfig.getKeyPatterns()));

        return stats != null ? stats : new ArrayList<>();
    }

    // attachMeta=false olan uçlar bugüne kadar meta (collectedAt/stale) taşımadı;
    // yanıt şekli değişmesin diye korunuyor.
    private <T extends MonitoredMetric> T collect(Long id, String operation,
                                                  BiFunction<CacheMonitor, InstanceRef, T> call,
                                                  Supplier<T> fallback,
                                                  boolean attachMeta) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, operation,
                () -> call.apply(monitor, instance));

        if (result.isTimeout()) {
            T timedOut = fallback.get();
            timedOut.setOverallStatus(MetricStatus.TIMEOUT);
            return timedOut;
        }
        if (result.isUnavailable() || result.value() == null) {
            return fallback.get();
        }
        T value = result.value();
        if (attachMeta) {
            value.setMeta(result.toMeta());
        }
        if (result.isStale()) {
            value.setOverallStatus(MetricStatus.STALE);
        }
        return value;
    }

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
