package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.CacheMonitor;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.monitor.ProtectedMonitorExecutor;
import com.nkolay.ucmd.service.KeyAnalysisService;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KeyAnalysisServiceImpl implements KeyAnalysisService {

    private final MonitoringService monitoringService;
    private final MonitorRegistry monitorRegistry;
    private final ProtectedMonitorExecutor executor;

    public KeyAnalysisServiceImpl(MonitoringService monitoringService,
                                  MonitorRegistry monitorRegistry,
                                  ProtectedMonitorExecutor executor) {
        this.monitoringService = monitoringService;
        this.monitorRegistry = monitorRegistry;
        this.executor = executor;
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(Long instanceId, String prefix, String cursor,
                                      int count, MatchMode matchMode) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        return executor.executeExpensive(instance, "scan-keys",
                () -> monitor.scanKeys(instance, prefix, cursor, count, matchMode));
    }

    @Override
    public TopKeysResponse getTopNKeys(Long instanceId, int n,Integer scanLimit) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        return executor.executeExpensive(instance, "top-keys",
                () -> monitor.getTopNKeys(instance, n, scanLimit));
    }

    @Override
    public KeyTtlResponse getKeyTTL(Long instanceId, String key) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        var result = executor.executeCachedMetric(instance, "key-ttl",
                () -> monitor.getKeyTTL(instance, key));
        if (result.isUnavailable() || result.value() == null) {
            return KeyTtlResponse.unavailable(key);
        }
        KeyTtlResponse response = result.value();
        if (result.isStale()) {
            response.getMeta().setStale(true);
        }
        return response;

    }
}