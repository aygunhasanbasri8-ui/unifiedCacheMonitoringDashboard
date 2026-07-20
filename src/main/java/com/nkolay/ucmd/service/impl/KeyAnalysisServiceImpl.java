package com.nkolay.ucmd.service.impl;


import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.model.dto.KeyInfo;
import com.nkolay.ucmd.model.dto.PageInfo;
import com.nkolay.ucmd.monitor.CacheMonitor;
import com.nkolay.ucmd.monitor.MonitorRegistry;
import com.nkolay.ucmd.service.KeyAnalysisService;
import com.nkolay.ucmd.service.MonitoringService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KeyAnalysisServiceImpl implements KeyAnalysisService {

    @Autowired
    public MonitoringService monitoringService;

    @Autowired
    public MonitorRegistry monitorRegistry;

    @Autowired
    public RedisTemplate<String, String> redisTemplate;



    public PageInfo<KeyInfo> scanKeys(Long instanceId, String prefix, String cursor, int count) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        if (instance == null) return new PageInfo<>(new ArrayList<>(), "0", false, count);

        CacheMonitor monitor = monitorRegistry.getMonitor(instance);
        return monitor.scanKeys(instance, prefix, cursor, count);
    }



    public List<KeyInfo> getTopNKeys(Long instanceId, int n) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        if (instance == null) return new ArrayList<>();

        CacheMonitor monitor = monitorRegistry.getMonitor(instance);
        return monitor.getTopNKeys(instance, n);
    }


    /**
     * GUNCELLENDI
     */
    public long getKeyTTL(Long instanceId, String key) {
        InstanceRef instance = monitoringService.getInstanceById(instanceId);
        if (instance == null) return -2;

        CacheMonitor monitor = monitorRegistry.getMonitor(instance);
        return monitor.getKeyTTL(instance, key);
    }
}
