package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.model.entity.InstanceRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MonitorRegistry {

    private final Map<String, CacheMonitor> monitors;

    public MonitorRegistry(List<CacheMonitor> monitorList) {

        monitors = monitorList.stream()
                .collect(Collectors.toMap(
                        CacheMonitor::getSupportedType,
                        Function.identity()
                ));
    }

    public CacheMonitor getMonitor(InstanceRef instance) {
        CacheMonitor monitor = monitors.get(instance.getType());
        if (monitor==null) {
            throw new IllegalArgumentException(
                    "Desteklenmeyen instance type: " + instance.getType()
            );
        }
        return monitor;
    }

    public java.util.Set<String> getSupportedTypes() {
        return monitors.keySet();
    }
}
