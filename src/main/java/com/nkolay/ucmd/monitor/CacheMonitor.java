package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;

import java.util.List;

public interface CacheMonitor {

    /**
     * REDIS HAZELCAST
     *
     */
    String getSupportedType();

    /**
     * Instance reachable
     *
     */
    boolean isReachable(InstanceRef instance);

    HealthInfo getHealth(InstanceRef instance);

    ResourceStats getResourceStats(InstanceRef instance);

    ConnectionStats getConnectionStats(InstanceRef instance);

    DataStats getDataStats(InstanceRef instance);

    ClusterInfo getClusterInfo(InstanceRef instance);

    PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor, int count, MatchMode matchMode);

    TopKeysResponse getTopNKeys(InstanceRef instance, int n, Integer scanLimit);

    KeyTtlResponse getKeyTTL(InstanceRef instance, String key);

    CacheEfficiencyStats getCacheEfficiency(InstanceRef instance);

    ReplicationInfo getReplicationInfo(InstanceRef instance);
    
    PersistenceInfo getPersistenceInfo(InstanceRef instance);

    List<KeyPatternStats> getKeyPatternStats(InstanceRef instance, List<KeyPatternConfig.PatternDef> patterns);
}
