package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.ClusterInfo;
import com.nkolay.ucmd.model.DataStats;
import com.nkolay.ucmd.model.ConnectionStats;
import com.nkolay.ucmd.model.ResourceStats;
import com.nkolay.ucmd.model.HealthInfo;
import com.nkolay.ucmd.model.InstanceRef;
import java.util.List;


public interface MonitoringService {

    // Instance CRUD
    List<InstanceRef> getAllInstances();
    InstanceRef getInstanceById(Long id);
    InstanceRef createInstance(InstanceRef instance);
    InstanceRef updateInstance(InstanceRef instance);
    void deleteInstance(Long id);

    // Metrics
    HealthInfo getInstanceHealth(Long id);
    ResourceStats getResourceStats(Long id);
    ConnectionStats getConnectionStats(Long id);
    DataStats getDataStats(Long id);
    ClusterInfo getClusterInfo(Long id);

}
