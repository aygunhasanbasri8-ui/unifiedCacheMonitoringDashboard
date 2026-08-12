package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.exception.InstanceNotFoundException;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.CacheMonitor;
import com.nkolay.ucmd.monitor.HazelcastConnectionManager;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.monitor.ProtectedMonitorExecutor;
import com.nkolay.ucmd.repository.InstanceRepository;
import com.nkolay.ucmd.service.MonitoringService;
import org.hibernate.Cache;
import org.hibernate.grammars.hql.HqlParser;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
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
    private final HazelcastConnectionManager hazelcastConnectionManager;

    @Value("${HAZELCAST_METRICS_URL:}")
    private String hazelcastMetricsUrl;

    public MonitoringServiceImpl(MonitorRegistry monitorRegistry, InstanceRepository instanceRepository, KeyPatternConfig keyPatternConfig, ProtectedMonitorExecutor executor, HazelcastConnectionManager hazelcastConnectionManager) {
        this.monitorRegistry = monitorRegistry;
        this.instanceRepository = instanceRepository;
        this.keyPatternConfig = keyPatternConfig;
        this.executor = executor;
        this.hazelcastConnectionManager = hazelcastConnectionManager;

    }

    @PostConstruct
    public void initDefaultInstance() {
        if (instanceRepository.count() == 0) {
            instanceRepository.save(new InstanceRef(null, "Redis-prod", InstanceType.REDIS, "localhost", 6379, true, false));
            instanceRepository.save(new InstanceRef(null, "Redis-cache", InstanceType.REDIS, "localhost", 6380, true, false));

            InstanceRef hzDev = new InstanceRef(null, "Hazelcast-dev", InstanceType.HAZELCAST,
                    "localhost", 5701, true, false);
            hzDev.setClusterName("dev");
            hzDev.setMembers("localhost:5701,localhost:5702");
            hzDev.setMetricsUrl("");
            instanceRepository.save(hzDev);

            InstanceRef hzAnalytics = new InstanceRef(null, "Hazelcast-analytics", InstanceType.HAZELCAST,
                    "localhost", 5703, true, false);
            hzAnalytics.setClusterName("analytics");
            hzAnalytics.setMembers("localhost:5703");
            hzAnalytics.setMetricsUrl(hazelcastMetricsUrl);
            instanceRepository.save(hzAnalytics);
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
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "resources",
                () -> monitor.getResourceStats(instance));

        if (result.isTimeout()) {
            ResourceStats s = unavailableResourceStats();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
        if (result.isUnavailable() || result.value() == null) {
            return unavailableResourceStats();
        }
        ResourceStats stats = result.value();
        stats.setMeta(result.toMeta());
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

        if (result.isTimeout()) {
            ConnectionStats s = unavailableConnectionStats();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
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

        if (result.isTimeout()) {
            DataStats s = unavailableDataStats();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
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

        if (result.isTimeout()) {
            ClusterInfo s = unavailableClusterInfo();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
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

        if (result.isTimeout()) {
            CacheEfficiencyStats s = unavailableCacheEfficiencyStats();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
        if (result.isUnavailable() || result.value() == null) {
            return unavailableCacheEfficiencyStats();
        }
        CacheEfficiencyStats stats = result.value();
        if (result.isStale()) {
            stats.setOverallStatus(MetricStatus.STALE);
        }
        return stats;
    }

    @Override
    public ReplicationInfo getReplicationInfo(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "replication",
                () -> monitor.getReplicationInfo(instance));

        if (result.isTimeout()) {
            ReplicationInfo s = ReplicationInfo.unavailable();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
        if (result.isUnavailable() || result.value() == null) {
            return ReplicationInfo.unavailable();
        }
        ReplicationInfo info = result.value();
        info.setMeta(result.toMeta());
        if (result.isStale()) {
            info.setOverallStatus(MetricStatus.STALE);
        }
        return info;
    }

    @Override
    public PersistenceInfo getPersistenceInfo(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "persistence",
                () -> monitor.getPersistenceInfo(instance));

        if (result.isTimeout()) {
            PersistenceInfo s = PersistenceInfo.unavailable();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
        if (result.isUnavailable() || result.value() == null) {
            return PersistenceInfo.unavailable();
        }
        PersistenceInfo info = result.value();
        info.setMeta(result.toMeta());
        if (result.isStale()) {
            info.setOverallStatus(MetricStatus.STALE);
        }
        return info;
    }

    @Override
    public MapStatsInfo getMapStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "map-stats",
                () -> monitor.getMapStats(instance));

        if (result.isTimeout()) {
            MapStatsInfo s = MapStatsInfo.unavailable();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
        if (result.isUnavailable() || result.value() == null) {
            return MapStatsInfo.unavailable();
        }
        MapStatsInfo info = result.value();
        info.setMeta(result.toMeta());
        if (result.isStale()) {
            info.setOverallStatus(MetricStatus.STALE);
        }
        return info;
    }

    @Override
    public OperationStatsInfo getOperationStats(Long id) {
        InstanceRef instance = getInstanceById(id);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "operation-stats",
                () -> monitor.getOperationStats(instance));

        if (result.isTimeout()) {
            OperationStatsInfo s = OperationStatsInfo.unavailable();
            s.setOverallStatus(MetricStatus.TIMEOUT);
            return s;
        }
        if (result.isUnavailable() || result.value() == null) {
            return OperationStatsInfo.unavailable();
        }
        OperationStatsInfo info = result.value();
        info.setMeta(result.toMeta());
        if (result.isStale()) {
            info.setOverallStatus(MetricStatus.STALE);
        }
        return info;
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
