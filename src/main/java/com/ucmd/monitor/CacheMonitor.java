package com.ucmd.monitor;

import com.ucmd.config.KeyPatternConfig;
import com.ucmd.enums.MatchMode;
import com.ucmd.model.dto.*;
import com.ucmd.model.entity.InstanceRef;

import java.util.List;

public interface CacheMonitor {

    /** InstanceRef.type ile eşleşen değer: "REDIS" veya "HAZELCAST". */
    String getSupportedType();

    boolean isReachable(InstanceRef instance);

    HealthInfo getHealth(InstanceRef instance);

    ResourceStats getResourceStats(InstanceRef instance);

    ConnectionStats getConnectionStats(InstanceRef instance);

    DataStats getDataStats(InstanceRef instance);

    ClusterInfo getClusterInfo(InstanceRef instance);

    MapStatsInfo getMapStats(InstanceRef instance);

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
