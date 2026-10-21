package com.nkolay.ucmd.service.impl;

import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.enums.MatchMode;
import com.nkolay.ucmd.exception.MapNotAllowedException;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.CacheMonitor;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.monitor.ProtectedMonitorExecutor;
import com.nkolay.ucmd.service.KeyAnalysisService;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.stereotype.Service;


@Service
public class KeyAnalysisServiceImpl implements KeyAnalysisService {

    private final MonitoringService monitoringService;
    private final MonitorRegistry monitorRegistry;
    private final ProtectedMonitorExecutor executor;
    private final KeySecurityConfig keySecurityConfig;

    public KeyAnalysisServiceImpl(MonitoringService monitoringService,
                                  MonitorRegistry monitorRegistry,
                                  ProtectedMonitorExecutor executor,
                                  KeySecurityConfig keySecurityConfig) {
        this.monitoringService = monitoringService;
        this.monitorRegistry = monitorRegistry;
        this.executor = executor;
        this.keySecurityConfig = keySecurityConfig;
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(Long instanceId, String prefix, String cursor,
                                      int count, MatchMode matchMode, String mapName) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        return executor.executeExpensive(instance, "scan-keys",
                () -> monitor.scanKeys(instance, prefix, cursor, count, matchMode, mapName));
    }

    @Override
    public TopKeysResponse getTopNKeys(Long instanceId, int n,Integer scanLimit) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        return executor.executeExpensive(instance, "top-keys",
                () -> monitor.getTopNKeys(instance, n, scanLimit));
    }

    @Override
    public KeyTtlResponse getKeyTTL(Long instanceId, String key, String mapName) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        CacheMonitor monitor = monitorRegistry.getMonitor(instance);

        // Allowlist kontrolü korumanın DIŞINDA: executeCachedMetric tüm exception'ları
        // yutup UNAVAILABLE döndürdüğü için 403 istemciye ulaşmazdı.
        // Monitor içindeki kontrol savunma derinliği olarak yerinde kalır.
        if (mapName != null && !mapName.isBlank()
                && !keySecurityConfig.isMapAllowed(mapName)) {
            throw new MapNotAllowedException("map izin verilen listede değil");
        }

        // Aynı key farklı map'lerde bulunabilir — cache anahtarı map'i de içermeli
        String operation = (mapName == null || mapName.isBlank())
                ? "key-ttl:" + key
                : "key-ttl:" + mapName + ":" + key;

        var result = executor.executeCachedMetric(instance, operation,"key-ttl",
                () -> monitor.getKeyTTL(instance, key, mapName));
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