package com.nkolay.ucmd.service;

import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
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
    MapStatsInfo getMapStats(Long id);
    OperationStatsInfo getOperationStats(Long id);
    CacheEfficiencyStats getCacheEfficiency(Long id);
    ReplicationInfo getReplicationInfo(Long id);
    PersistenceInfo getPersistenceInfo(Long id);
    List<KeyPatternStats> getKeyPatternStats(Long id);

}
