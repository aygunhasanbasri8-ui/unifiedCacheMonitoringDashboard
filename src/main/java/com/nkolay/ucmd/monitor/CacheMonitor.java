package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.model.*;
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

    PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor, int count);

    List<KeyInfo> getTopNKeys(InstanceRef instance, int n);

    long getKeyTTL(InstanceRef instanceId, String key);
}
