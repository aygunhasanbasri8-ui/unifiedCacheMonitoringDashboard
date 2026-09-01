package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.enums.MatchMode;
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

    /** Rehber §5.3 — map/data structure istatistikleri. Redis'te NOT_APPLICABLE. */
    MapStatsInfo getMapStats(InstanceRef instance);

    /** Rehber §5.5 — operation/event metrikleri. Redis'te NOT_APPLICABLE. */
    OperationStatsInfo getOperationStats(InstanceRef instance);

    /** @param mapName Hazelcast'te aranacak IMap; null ise map adları listelenir. Redis'te yok sayılır. */
    PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor, int count,
                               MatchMode matchMode, String mapName);

    TopKeysResponse getTopNKeys(InstanceRef instance, int n, Integer scanLimit);

    /** @param mapName Hazelcast'te entry'nin bulunduğu IMap. Redis'te yok sayılır. */
    KeyTtlResponse getKeyTTL(InstanceRef instance, String key, String mapName);

    CacheEfficiencyStats getCacheEfficiency(InstanceRef instance);

    ReplicationInfo getReplicationInfo(InstanceRef instance);
    
    PersistenceInfo getPersistenceInfo(InstanceRef instance);

    List<KeyPatternStats> getKeyPatternStats(InstanceRef instance, List<KeyPatternConfig.PatternDef> patterns);
}
