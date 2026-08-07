package com.nkolay.ucmd.monitor;

import com.hazelcast.cluster.Cluster;
import com.hazelcast.cluster.Member;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.partition.Partition;
import com.hazelcast.partition.PartitionService;
import com.nkolay.ucmd.config.KeyPatternConfig;
import com.nkolay.ucmd.config.KeySecurityConfig;
import com.nkolay.ucmd.exception.MapNotAllowedException;
import com.nkolay.ucmd.model.dto.*;
import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.prometheus.NodeMetrics;
import com.nkolay.ucmd.security.KeyMasker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class HazelcastMonitor implements CacheMonitor {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMonitor.class);
    private final HazelcastConnectionManager connectionManager;
    private final HazelcastMetricsClient metricsClient;
    private final KeyMasker keyMasker;
    private final KeySecurityConfig keySecurityConfig;

    /** Rehber §5.7 — map içi entry taramasında üst sınır (tam tarama yasak). */
    @Value("${ucmd.key-search.hazelcast-max-scan-entries:5000}")
    private int hazelcastMaxScanEntries;


    public HazelcastMonitor(HazelcastConnectionManager connectionManager,
                            HazelcastMetricsClient metricsClient,
                            KeyMasker keyMasker,
                            KeySecurityConfig keySecurityConfig) {
        this.connectionManager = connectionManager;
        this.metricsClient = metricsClient;
        this.keyMasker = keyMasker;
        this.keySecurityConfig = keySecurityConfig;
    }

    @Override
    public String getSupportedType() {
        return "HAZELCAST";
    }

    @Override
    public boolean isReachable(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) return false;
            return client.getLifecycleService().isRunning()
                    && !client.getCluster().getMembers().isEmpty();
        } catch (Exception e) {
            logger.warn("Hazelcast reachability check failed for {}: {}",
                    instance.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public HealthInfo getHealth(InstanceRef instance) {

        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null || !isReachable(instance)) {
                return new HealthInfo("DOWN", "unknown", 0);
            }

            Cluster cluster = client.getCluster();

            // Rehber §5.6 — gerçek member uptime Prometheus'ta (runtime.uptime, ms).
            // Prometheus yoksa cluster time farkına düşülür.
            List<NodeMetrics> nodes = metricsClient.fetchAllNodes(instance);
            Double uptimeMs = HazelcastMetricsClient.max(nodes, n -> n.hz("uptime", "runtime"));
            long uptimeSeconds = (uptimeMs != null)
                    ? (long) (uptimeMs / 1000)
                    : Math.max(0, (System.currentTimeMillis() - cluster.getClusterTime()) / 1000);

            String version = cluster.getMembers().stream()
                    .findFirst()
                    .map(m -> m.getVersion().toString())
                    .orElse("unknown");
            return new HealthInfo("UP", version, uptimeSeconds);

        } catch (Exception e) {
            logger.error("Error fetching Hazelcast health: {}", e.getMessage(), e);
            return new HealthInfo("DOWN", "unknown", 0);
        }
    }

    @Override
    public ResourceStats getResourceStats(InstanceRef instance) {
        try {
            List<NodeMetrics> nodes = metricsClient.fetchAllNodes(instance);

            if (nodes.isEmpty()) {
                logger.debug("Hazelcast metrics unavailable for {}", instance.getName());
                return unavailableResourceStats(MetricStatus.NOT_APPLICABLE);
            }

            // Cluster toplam heap kullanımı (tüm node'lar toplanır)
            Double usedHeap = HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_memory_bytes_used", "area", "heap"));
            Double maxHeap = HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_memory_bytes_max", "area", "heap"));

            // CPU: node ortalaması (oransal değer, toplanmaz).
            // processcpuload iki yerde var — prefix="os" olanı doğrusu.
            Double cpuLoad = HazelcastMetricsClient.avg(nodes,
                    n -> n.hz("processcpuload", "os"));

            logger.debug("HZ resources: {} node, heap {} bytes", nodes.size(), usedHeap);

            ResourceStats stats = new ResourceStats(
                    cpuLoad != null ? mul100(cpuLoad) : 0.0,
                    usedHeap != null ? usedHeap.longValue() : 0,
                    maxHeap != null ? maxHeap.longValue() : 0);

            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setCpuStats(cpuLoad != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            stats.setMemoryStatus(usedHeap != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            stats.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);

            // ── §5.6 JVM metrikleri ──
            stats.setSystemCpuPercent(mul100(HazelcastMetricsClient.avg(nodes,
                    n -> n.hz("systemcpuload", "os"))));
            stats.setSystemLoadAverage(HazelcastMetricsClient.avg(nodes,
                    n -> n.hz("systemloadaverage", "os")));

            stats.setNonHeapUsedBytes(toLong(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_memory_bytes_used", "area", "nonheap"))));
            stats.setNonHeapMaxBytes(toLong(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_memory_bytes_max", "area", "nonheap"))));

            // GC: tüm collector'lar (G1 Young + G1 Old) toplanır
            stats.setGcCollectionCount(toLong(HazelcastMetricsClient.sum(nodes,
                    n -> n.sumByLabel("jvm_gc_collection_seconds_count", "gc"))));
            stats.setGcCollectionSeconds(HazelcastMetricsClient.sum(nodes,
                    n -> n.sumByLabel("jvm_gc_collection_seconds_sum", "gc")));

            stats.setThreadCount(toInt(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_threads_current"))));
            stats.setPeakThreadCount(toInt(HazelcastMetricsClient.max(nodes,
                    n -> n.value("jvm_threads_peak"))));
            stats.setDaemonThreadCount(toInt(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_threads_daemon"))));
            stats.setDeadlockedThreadCount(toInt(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_threads_deadlocked"))));

            stats.setOpenFileDescriptors(toLong(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("process_open_fds"))));
            stats.setMaxFileDescriptors(toLong(HazelcastMetricsClient.max(nodes,
                    n -> n.value("process_max_fds"))));

            stats.setLoadedClassCount(toLong(HazelcastMetricsClient.sum(nodes,
                    n -> n.value("jvm_classes_currently_loaded"))));

            // Member uptime — millisaniye cinsinden gelir
            Double uptimeMs = HazelcastMetricsClient.max(nodes, n -> n.hz("uptime", "runtime"));
            stats.setMemberUptimeSeconds(uptimeMs != null ? (long) (uptimeMs / 1000) : null);

            stats.setJvmStatus(stats.getNonHeapUsedBytes() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            stats.setGcStatus(stats.getGcCollectionCount() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            stats.setThreadStatus(stats.getThreadCount() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            stats.setFileDescriptorStatus(stats.getOpenFileDescriptors() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);

            return stats;

        } catch (Exception e) {
            logger.error("Error fetching Hazelcast resource stats: {}", e.getMessage());
            return unavailableResourceStats(MetricStatus.UNAVAILABLE);
        }
    }

    /** Kaynak metrikleri alınamadığında dönen boş yanıt. */
    private ResourceStats unavailableResourceStats(MetricStatus fragmentationStatus) {
        ResourceStats down = new ResourceStats(0, 0, 0);
        down.setOverallStatus(MetricStatus.UNAVAILABLE);
        down.setCpuStats(MetricStatus.UNAVAILABLE);
        down.setMemoryStatus(MetricStatus.UNAVAILABLE);
        down.setFragmentationStatus(fragmentationStatus);
        down.setJvmStatus(MetricStatus.UNAVAILABLE);
        down.setGcStatus(MetricStatus.UNAVAILABLE);
        down.setThreadStatus(MetricStatus.UNAVAILABLE);
        down.setFileDescriptorStatus(MetricStatus.UNAVAILABLE);
        return down;
    }

    // ── null-safe dönüştürücüler ──
    private Double mul100(Double v) { return v == null ? null : Math.round(v * 10000.0) / 100.0; }
    private Long toLong(Double v) { return v == null ? null : v.longValue(); }
    private Integer toInt(Double v) { return v == null ? null : v.intValue(); }

    @Override
    public OperationStatsInfo getOperationStats(InstanceRef instance) {
        try {
            List<NodeMetrics> nodes = metricsClient.fetchAllNodes(instance);
            if (nodes.isEmpty()) return OperationStatsInfo.unavailable();

            OperationStatsInfo o = new OperationStatsInfo();

            // ── operation ──
            o.setQueueSize(sumHz(nodes, "queuesize", "operation"));
            o.setPriorityQueueSize(sumHz(nodes, "priorityqueuesize", "operation"));
            o.setRunningCount(sumHz(nodes, "runningcount", "operation"));
            o.setCompletedCount(sumHz(nodes, "completedcount", "operation"));
            o.setResponseQueueSize(sumHz(nodes, "responsequeuesize", "operation"));
            o.setGenericQueueSize(sumHz(nodes, "genericqueuesize", "operation"));
            o.setPartitionThreadCount(toInt(HazelcastMetricsClient.sum(nodes,
                    n -> n.hz("partitionthreadcount", "operation"))));
            o.setGenericThreadCount(toInt(HazelcastMetricsClient.sum(nodes,
                    n -> n.hz("genericthreadcount", "operation"))));

            // ── operation.invocations ──
            o.setPendingInvocations(sumHz(nodes, "pending", "operation.invocations"));
            o.setBackupTimeouts(sumHz(nodes, "backuptimeouts", "operation.invocations"));
            o.setNormalTimeouts(sumHz(nodes, "normaltimeouts", "operation.invocations"));

            // ── operation.responses (scrape'te prefix="operation", ad responses_ ön ekli) ──
            o.setResponseErrorCount(sumHz(nodes, "responses_errorcount", "operation"));
            o.setResponseTimeoutCount(sumHz(nodes, "responses_timeoutcount", "operation"));
            o.setResponseMissingCount(sumHz(nodes, "responses_missingcount", "operation"));

            // ── event ──
            o.setEventQueueSize(sumHz(nodes, "eventqueuesize", "event"));
            o.setEventsProcessed(sumHz(nodes, "eventsprocessed", "event"));
            o.setEventRejectedCount(sumHz(nodes, "rejectedcount", "event"));
            o.setSyncDeliveryFailureCount(sumHz(nodes, "syncdeliveryfailurecount", "event"));

            // ── executor.internal ──
            Set<String> executorNames = new LinkedHashSet<>();
            for (NodeMetrics n : nodes) executorNames.addAll(n.objectNames("executor.internal"));

            List<OperationStatsInfo.ExecutorDetail> execs = new ArrayList<>();
            for (String name : executorNames) {
                var d = new OperationStatsInfo.ExecutorDetail(name);
                d.setQueueSize(sumHzObj(nodes, "queuesize", "executor.internal", name));
                d.setPoolSize(sumHzObj(nodes, "poolsize", "executor.internal", name));
                // Scrape'teki gerçek ad "completedtasks" ("completedtaskcount" yok)
                d.setCompletedTaskCount(
                        sumHzObj(nodes, "completedtasks", "executor.internal", name));
                d.setRemainingQueueCapacity(
                        sumHzObj(nodes, "remainingqueuecapacity", "executor.internal", name));
                execs.add(d);
            }
            o.setExecutors(execs);

            o.setOverallStatus(MetricStatus.AVAILABLE);
            o.setOperationStatus(o.getQueueSize() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            o.setEventStatus(o.getEventQueueSize() != null
                    ? MetricStatus.AVAILABLE : MetricStatus.UNAVAILABLE);
            o.setExecutorStatus(execs.isEmpty()
                    ? MetricStatus.UNAVAILABLE : MetricStatus.AVAILABLE);
            return o;

        } catch (Exception e) {
            logger.error("Error fetching operation stats for {}: {}",
                    instance.getName(), e.getMessage());
            return OperationStatsInfo.unavailable();
        }
    }

    // ── operation/event metrik yardımcıları (rehber §5.5) ──

    private Long sumHz(List<NodeMetrics> nodes, String metric, String prefix) {
        Double v = HazelcastMetricsClient.sum(nodes, n -> n.hz(metric, prefix));
        return v == null ? null : v.longValue();
    }

    private Long sumHzObj(List<NodeMetrics> nodes, String metric, String prefix, String obj) {
        Double v = HazelcastMetricsClient.sum(nodes, n -> n.hz(metric, prefix, obj));
        return v == null ? null : v.longValue();
    }

    // ── map metrik yardımcıları (rehber §5.3) ──

    private Long sumMapMetric(List<NodeMetrics> nodes, String metric, String mapName) {
        Double v = HazelcastMetricsClient.sum(nodes, n -> n.hz(metric, "map", mapName));
        return v == null ? null : v.longValue();
    }

    private Long sumMapMetricMax(List<NodeMetrics> nodes, String metric, String mapName) {
        Double v = HazelcastMetricsClient.max(nodes, n -> n.hz(metric, "map", mapName));
        return v == null ? null : v.longValue();
    }

    /** Hazelcast latency toplamları mikrosaniye cinsindendir. */
    private Double avgLatency(Long totalMicros, Long opCount) {
        if (totalMicros == null || opCount == null || opCount == 0) return null;
        return Math.round(((double) totalMicros / opCount / 1000.0) * 1000.0) / 1000.0;
    }

    @Override
    public ConnectionStats getConnectionStats(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                return unavailableConnectionStats();
            }

            // Rehber §5.2 — gerçek client sayısı tcp.connection prefix'inde.
            // Member sayısı ClusterInfo.nodeCount'ta zaten var, burada tekrarlanmaz.
            List<NodeMetrics> nodes = metricsClient.fetchAllNodes(instance);
            ConnectionStats stats = new ConnectionStats(0, 0, 0);

            Double clientCount = HazelcastMetricsClient.sum(nodes,
                    n -> n.hz("clientcount", "tcp.connection"));
            Double activeConn = HazelcastMetricsClient.sum(nodes,
                    n -> n.hz("activecount", "tcp.connection"));

            if (clientCount != null) {
                stats.setActiveClients(clientCount.intValue());
                stats.setActiveClientStatus(MetricStatus.AVAILABLE);
            } else {
                stats.setActiveClientStatus(MetricStatus.UNAVAILABLE);
            }
            if (activeConn != null) {
                stats.setTotalConnectionsReceived(activeConn.longValue());
            }

            // Hazelcast'te maxclients kavramı yok
            stats.setMaxClientsStatus(MetricStatus.NOT_APPLICABLE);
            stats.setBlockedClientsStatus(MetricStatus.NOT_APPLICABLE);
            stats.setErrorStatus(MetricStatus.NOT_APPLICABLE);
            stats.setOverallStatus(nodes.isEmpty()
                    ? MetricStatus.UNAVAILABLE : MetricStatus.AVAILABLE);
            return stats;

        } catch (Exception e) {
            logger.error("Error fetching connection stats for {}: {}",
                    instance.getName(), e.getMessage());
            return unavailableConnectionStats();
        }
    }

    private ConnectionStats unavailableConnectionStats() {
        ConnectionStats down = new ConnectionStats(0, 0, 0);
        down.setOverallStatus(MetricStatus.UNAVAILABLE);
        down.setActiveClientStatus(MetricStatus.UNAVAILABLE);
        down.setErrorStatus(MetricStatus.UNAVAILABLE);
        down.setMaxClientsStatus(MetricStatus.UNAVAILABLE);
        down.setBlockedClientsStatus(MetricStatus.UNAVAILABLE);
        return down;
    }


    @Override
    public DataStats getDataStats(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                DataStats down = new DataStats(0, 0, 0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setMemoryStatus(MetricStatus.UNAVAILABLE);
                down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
                down.setEvictionStatus(MetricStatus.UNAVAILABLE);
                down.setTtlStatus(MetricStatus.UNAVAILABLE);
                return down;
            }

            long totalEntries = 0;
            for (String mapName : client.getDistributedObjects()
                    .stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .map(obj -> obj.getName())
                    .toList()) {
                totalEntries += client.getMap(mapName).size();

            }
            DataStats stats = new DataStats(totalEntries, 0, 0);
            stats.setOverallStatus(MetricStatus.AVAILABLE);
            stats.setMemoryStatus(MetricStatus.NOT_APPLICABLE);
            stats.setFragmentationStatus(MetricStatus.NOT_APPLICABLE);
            stats.setEvictionStatus(MetricStatus.NOT_APPLICABLE);
            stats.setTtlStatus(MetricStatus.NOT_APPLICABLE);
            return stats;


        } catch (Exception e) {
            DataStats down = new DataStats(0,0,0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setMemoryStatus(MetricStatus.UNAVAILABLE);
            down.setFragmentationStatus(MetricStatus.UNAVAILABLE);
            down.setEvictionStatus(MetricStatus.UNAVAILABLE);
            down.setTtlStatus(MetricStatus.UNAVAILABLE);
            return down;
        }

    }


    @Override
    public ClusterInfo getClusterInfo(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null || !isReachable(instance)) {
                ClusterInfo down = new ClusterInfo();
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
                down.setDistributionStatus(MetricStatus.UNAVAILABLE);
                down.setSafetyStatus(MetricStatus.UNAVAILABLE);
                return down;
            }

            Cluster cluster = client.getCluster();
            Set<Member> members = cluster.getMembers();

            ClusterInfo info = new ClusterInfo();
            info.setNodeCount(members.size());
            info.setClusterName(client.getConfig().getClusterName());
            info.setOverallStatus(MetricStatus.AVAILABLE);

            // Hazelcast üye setini yaşa göre sıralar — ilk üye master'dır
            List<ClusterInfo.MemberInfo> memberInfoList = new ArrayList<>();
            boolean isFirst = true;
            for (Member member : members) {
                memberInfoList.add(new ClusterInfo.MemberInfo(
                        member.getUuid().toString(),
                        member.getAddress().getHost(),
                        member.getAddress().getPort(),
                        isFirst));
                isFirst = false;
            }
            info.setMembers(memberInfoList);

            applyPartitionMetrics(client, info);

            try {
                PartitionService ps = client.getPartitionService();
                info.setClusterSafe(ps.isClusterSafe());
                info.setSafetyStatus(MetricStatus.AVAILABLE);
            } catch (UnsupportedOperationException e) {
                info.setClusterSafe(null);
                info.setSafetyStatus(MetricStatus.NOT_APPLICABLE);
            } catch (Exception e) {
                info.setClusterSafe(null);
                info.setSafetyStatus(MetricStatus.UNAVAILABLE);
            }

            return info;

        } catch (Exception e) {
            logger.error("Error fetching cluster info: {}", e.getMessage());
            ClusterInfo down = new ClusterInfo();
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
            down.setDistributionStatus(MetricStatus.UNAVAILABLE);
            down.setSafetyStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }

    /** Rehber §5.4 — gerçek partition sayısı, member başına dağılım ve skew. */
    private void applyPartitionMetrics(HazelcastInstance client, ClusterInfo info) {
        try {
            PartitionService ps = client.getPartitionService();
            Set<Partition> partitions = ps.getPartitions();

            if (partitions == null || partitions.isEmpty()) {
                info.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
                info.setDistributionStatus(MetricStatus.UNAVAILABLE);
                return;
            }

            info.setPartitionCount(partitions.size());
            info.setPartitionCountStatus(MetricStatus.AVAILABLE);

            Map<String, Integer> distribution = new LinkedHashMap<>();
            int unassigned = 0;
            for (Partition p : partitions) {
                Member owner = p.getOwner();
                if (owner == null) { unassigned++; continue; }
                String key = owner.getAddress().getHost() + ":" + owner.getAddress().getPort();
                distribution.merge(key, 1, Integer::sum);
            }

            info.setPartitionDistribution(distribution);
            info.setUnassignedPartitions(unassigned);

            if (distribution.isEmpty()) {
                info.setDistributionStatus(MetricStatus.UNAVAILABLE);
                info.setBalanced(null);
                return;
            }

            int max = distribution.values().stream().mapToInt(Integer::intValue).max().orElse(0);
            int min = distribution.values().stream().mapToInt(Integer::intValue).min().orElse(0);
            double avg = (double) partitions.size() / distribution.size();
            double skew = (avg > 0) ? ((max - min) / avg) * 100.0 : 0.0;

            info.setDistributionSkewPercent(Math.round(skew * 100.0) / 100.0);
            info.setBalanced(skew <= 10.0 && unassigned == 0);
            info.setDistributionStatus(unassigned > 0
                    ? MetricStatus.ESTIMATED : MetricStatus.AVAILABLE);
            info.setBalancedStatus(MetricStatus.AVAILABLE);

        } catch (UnsupportedOperationException e) {
            info.setPartitionCountStatus(MetricStatus.NOT_APPLICABLE);
            info.setDistributionStatus(MetricStatus.NOT_APPLICABLE);
            info.setBalanced(null);
        } catch (Exception e) {
            logger.warn("Partition metrics unavailable: {}", e.getMessage());
            info.setPartitionCountStatus(MetricStatus.UNAVAILABLE);
            info.setDistributionStatus(MetricStatus.UNAVAILABLE);
            info.setBalanced(null);
        }
    }

    @Override
    public MapStatsInfo getMapStats(InstanceRef instance) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                return MapStatsInfo.unavailable();
            }

            List<NodeMetrics> nodes = metricsClient.fetchAllNodes(instance);

            MapStatsInfo result = new MapStatsInfo();
            List<MapStatsInfo.MapDetail> details = new ArrayList<>();
            long totalEntries = 0;

            // Map adlarını client'tan al (kaynak doğrusu), metrikleri Prometheus'tan
            for (var obj : client.getDistributedObjects()) {
                if (!(obj instanceof com.hazelcast.map.IMap)) continue;
                String mapName = obj.getName();
                long size = client.getMap(mapName).size();
                totalEntries += size;

                // Yanıttaki ad maskeli, Prometheus sorgusu HAM mapName ile yapılır
                MapStatsInfo.MapDetail d =
                        new MapStatsInfo.MapDetail(keyMasker.mask(mapName), size);

                if (!nodes.isEmpty()) {
                    d.setOwnedEntryCount(sumMapMetric(nodes, "ownedentrycount", mapName));
                    d.setBackupEntryCount(sumMapMetric(nodes, "backupentrycount", mapName));
                    d.setOwnedEntryMemoryCost(sumMapMetric(nodes, "ownedentrymemorycost", mapName));
                    d.setBackupEntryMemoryCost(sumMapMetric(nodes, "backupentrymemorycost", mapName));
                    d.setHeapCost(sumMapMetric(nodes, "heapcost", mapName));
                    d.setHits(sumMapMetric(nodes, "hits", mapName));
                    d.setGetCount(sumMapMetric(nodes, "getcount", mapName));
                    d.setPutCount(sumMapMetric(nodes, "putcount", mapName));
                    d.setRemoveCount(sumMapMetric(nodes, "removecount", mapName));
                    d.setLockedEntryCount(sumMapMetric(nodes, "lockedentrycount", mapName));
                    d.setDirtyEntryCount(sumMapMetric(nodes, "dirtyentrycount", mapName));
                    d.setEvictionCount(sumMapMetric(nodes, "evictioncount", mapName));
                    d.setExpirationCount(sumMapMetric(nodes, "expirationcount", mapName));

                    Double bc = HazelcastMetricsClient.max(nodes,
                            n -> n.hz("backupcount", "map", mapName));
                    d.setBackupCount(bc != null ? bc.intValue() : null);

                    d.setLastAccessTime(sumMapMetricMax(nodes, "lastaccesstime", mapName));
                    d.setLastUpdateTime(sumMapMetricMax(nodes, "lastupdatetime", mapName));

                    // Ortalama gecikme: toplam latency / operasyon sayısı (mikrosaniye → ms)
                    d.setAvgGetLatencyMillis(avgLatency(
                            sumMapMetric(nodes, "totalgetlatency", mapName), d.getGetCount()));
                    d.setAvgPutLatencyMillis(avgLatency(
                            sumMapMetric(nodes, "totalputlatency", mapName), d.getPutCount()));

                    // Hit ratio: hits / getCount. Payda 0 ise null (rehber §4.4 ilkesi)
                    if (d.getGetCount() != null && d.getGetCount() > 0 && d.getHits() != null) {
                        d.setHitRatio((double) d.getHits() / d.getGetCount());
                    }
                }

                details.add(d);
            }

            result.setMapCount(details.size());
            result.setTotalEntryCount(totalEntries);
            result.setMaps(details);
            result.setOverallStatus(MetricStatus.AVAILABLE);
            result.setMemoryCostStatus(nodes.isEmpty()
                    ? MetricStatus.UNAVAILABLE : MetricStatus.AVAILABLE);
            result.setOperationStatus(nodes.isEmpty()
                    ? MetricStatus.UNAVAILABLE : MetricStatus.AVAILABLE);
            return result;

        } catch (Exception e) {
            logger.error("Error fetching map stats for {}: {}",
                    instance.getName(), e.getMessage());
            return MapStatsInfo.unavailable();
        }
    }

    @Override
    public PageInfo<KeyInfo> scanKeys(InstanceRef instance, String prefix, String cursor,
                                      int count, MatchMode matchMode, String mapName) {

        final String search = (prefix == null) ? "" : prefix.trim();
        final MatchMode mode = (matchMode == null ) ? MatchMode.STARTS_WITH : matchMode;

        final HazelcastInstance client = connectionManager.getClient(instance);
        if (client == null) {
            return PageInfo.unavailable(count);
        }

        // mapName boşsa: IMap adlarını listele (dashboard'da map seçimi için)
        if (mapName == null || mapName.isBlank()) {
            return scanMapNames(client, instance, search, mode, count);
        }
        // mapName doluysa: o map içindeki entry key'lerini ara (rehber §5.7)
        return scanMapEntries(client, instance, mapName, search, mode, cursor, count);
    }

    /** IMap adlarını listeler — eski davranış, map seçimi için korunur. */
    private PageInfo<KeyInfo> scanMapNames(HazelcastInstance client, InstanceRef instance,
                                           String search, MatchMode mode, int count) {
        List<KeyInfo> keys = new ArrayList<>();
        try {
            client.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .filter(obj -> search.isEmpty() || (mode == MatchMode.CONTAINS)
                            ? obj.getName().contains(search)
                            : obj.getName().startsWith(search) )
                    .limit(count)
                    .forEach(obj -> {
                        com.hazelcast.map.IMap<?, ?> map =
                                client.getMap(obj.getName());
                        KeyInfo ki = new KeyInfo(keyMasker.mask(obj.getName()), "map", 0, -1);
                        ki.setEntryCount((long) map.size());
                        ki.setSizeStatus(MetricStatus.NOT_APPLICABLE);   // byte boyutu alınamaz
                        keys.add(ki);

                    });
        } catch (Exception e) {
            logger.error("Error scanning maps for instance {}: {}", instance.getName(), e.getMessage());
            return PageInfo.unavailable(count);
        }

        return new PageInfo<>(keys, "0", false, count);
    }

    /**
     * Rehber §5.7 — map içi entry araması. Kontrollü sampling, tam tarama yok:
     * en çok {@code hazelcastMaxScanEntries} entry taranır, sonuç ESTIMATED işaretlenir.
     */
    private PageInfo<KeyInfo> scanMapEntries(HazelcastInstance client, InstanceRef instance,
                                             String mapName, String search, MatchMode mode,
                                             String cursor, int count) {
        if (!keySecurityConfig.isMapAllowed(mapName)) {
            throw new MapNotAllowedException("map izin verilen listede değil");
        }

        // Hazelcast'te cursor yok — offset olarak yorumlanır
        int offset = 0;
        try {
            if (cursor != null && !cursor.isBlank()) offset = Math.max(0, Integer.parseInt(cursor.trim()));
        } catch (NumberFormatException ignored) { }

        List<KeyInfo> keys = new ArrayList<>();
        int scanned = 0;
        int skipped = 0;
        int collected = 0;
        boolean scanLimitHit = false;

        try {
            IMap<Object, Object> map = client.getMap(mapName);
            int mapSize = map.size();

            // keySet() lazy değildir — büyük map'te tüm key'ler client'a çekilir.
            if (mapSize > hazelcastMaxScanEntries) {
                logger.warn("Map '{}' entry sayısı ({}) tarama sınırını ({}) aşıyor — arama reddedildi",
                        keyMasker.maskForLog(mapName), mapSize, hazelcastMaxScanEntries);
                PageInfo<KeyInfo> tooLarge = PageInfo.unavailable(count);
                tooLarge.setStatus(MetricStatus.NOT_APPLICABLE);
                return tooLarge;
            }
            for (Object k : map.keySet()) {
                scanned++;
                if (scanned > hazelcastMaxScanEntries) { scanLimitHit = true; break; }

                String keyStr = String.valueOf(k);
                boolean matches = search.isEmpty()
                        || (mode == MatchMode.CONTAINS ? keyStr.contains(search)
                                                       : keyStr.startsWith(search));
                if (!matches) continue;

                if (skipped < offset) { skipped++; continue; }

                EntryView<Object, Object> view = map.getEntryView(k);
                long ttl = (view != null && view.getExpirationTime() > 0
                            && view.getExpirationTime() < Long.MAX_VALUE)
                        ? Math.max(0, view.getExpirationTime() - System.currentTimeMillis())
                        : -1;

                KeyInfo ki = new KeyInfo(keyMasker.mask(keyStr), "entry",
                        view != null ? view.getCost() : 0, ttl);
                ki.setSizeStatus(view != null ? MetricStatus.ESTIMATED : MetricStatus.UNAVAILABLE);
                keys.add(ki);

                if (++collected >= count) break;
            }

            // Örneklem tabanlı — tam tarama garantisi yok
            boolean hasMore = scanLimitHit || mapSize > hazelcastMaxScanEntries
                    || (offset + collected) < mapSize;
            String nextCursor = String.valueOf(offset + collected);

            logger.debug("Scanned {} entries on {} (limit {})",
                    scanned, instance.getName(), hazelcastMaxScanEntries);

            return new PageInfo<>(keys, nextCursor, hasMore, count, MetricStatus.ESTIMATED);

        } catch (MapNotAllowedException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error scanning entries for instance {}: {}",
                    instance.getName(), e.getMessage());
            return PageInfo.unavailable(count);
        }
    }

    @Override
    public TopKeysResponse getTopNKeys(InstanceRef instance, int n, Integer scanLimit) {
        List<KeyInfo> keys = new ArrayList<>();
        int scanned = 0;

        HazelcastInstance client = connectionManager.getClient(instance);
        if (client == null) {
            return TopKeysResponse.unavailable();
        }

        try {
            for (var obj : client.getDistributedObjects()) {
                if (!(obj instanceof com.hazelcast.map.IMap)) continue;
                com.hazelcast.map.IMap<?, ?> map = client.getMap(obj.getName());
                KeyInfo ki = new KeyInfo(keyMasker.mask(obj.getName()), "map", 0, -1);
                ki.setEntryCount((long) map.size());
                ki.setSizeStatus(MetricStatus.NOT_APPLICABLE);   // byte boyutu alınamaz
                keys.add(ki);
                scanned++;
            }
        } catch (Exception e) {
            logger.error("Error fetching top N maps for {}: {}", instance.getName(), e.getMessage());
            return TopKeysResponse.unavailable();
        }

        keys.sort((a, b) -> Long.compare(
                b.getEntryCount() != null ? b.getEntryCount() : 0,
                a.getEntryCount() != null ? a.getEntryCount() : 0));
        List<KeyInfo> top = new ArrayList<>(keys.stream().limit(n).toList());
        TopKeysResponse response = new TopKeysResponse(top, scanned, scanned, true);
        response.setEstimated(true);
        response.setStatus(MetricStatus.ESTIMATED);

        return response;
    }

    @Override
    public KeyTtlResponse getKeyTTL(InstanceRef instance, String key, String mapName) {
        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                return KeyTtlResponse.unavailable(key);
            }

            // mapName yoksa eski davranış: key bir map adı mı?
            if (mapName == null || mapName.isBlank()) {
                boolean isMap = client.getDistributedObjects().stream()
                        .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                        .anyMatch(obj -> obj.getName().equals(key));
                return isMap ? new KeyTtlResponse(key, -1, TtlStatus.PERSISTENT)
                             : new KeyTtlResponse(key, -2, TtlStatus.NOT_FOUND);
            }

            // mapName varsa: o map içindeki entry'nin TTL'i (rehber §5.7)
            if (!keySecurityConfig.isMapAllowed(mapName)) {
                throw new MapNotAllowedException("map izin verilen listede değil");
            }

            IMap<Object, Object> map = client.getMap(mapName);
            EntryView<Object, Object> view = map.getEntryView(key);
            if (view == null) return new KeyTtlResponse(key, -2, TtlStatus.NOT_FOUND);

            long exp = view.getExpirationTime();
            if (exp <= 0 || exp == Long.MAX_VALUE) {
                return new KeyTtlResponse(key, -1, TtlStatus.PERSISTENT);
            }
            long remaining = Math.max(0, exp - System.currentTimeMillis());
            return new KeyTtlResponse(key, remaining, TtlStatus.EXPIRING);

        } catch (MapNotAllowedException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error fetching TTL for '{}' on {}: {}",
                    keyMasker.maskForLog(key), instance.getName(), e.getMessage());
            return KeyTtlResponse.unavailable(key);
        }
    }

    @Override
    public CacheEfficiencyStats getCacheEfficiency(InstanceRef instance) {
        try {
            List<NodeMetrics> nodes = metricsClient.fetchAllNodes(instance);
            if (nodes.isEmpty()) {
                CacheEfficiencyStats down = new CacheEfficiencyStats(0, 0);
                down.setOverallStatus(MetricStatus.UNAVAILABLE);
                down.setHitRatioStatus(MetricStatus.UNAVAILABLE);
                return down;
            }

            long totalHits = 0;
            long totalGets = 0;
            for (NodeMetrics node : nodes) {
                for (String mapName : node.objectNames("map")) {
                    Double h = node.hz("hits", "map", mapName);
                    Double g = node.hz("getcount", "map", mapName);
                    if (h != null) totalHits += h.longValue();
                    if (g != null) totalGets += g.longValue();
                }
            }

            long misses = Math.max(0, totalGets - totalHits);
            CacheEfficiencyStats ce = new CacheEfficiencyStats(totalHits, misses);

            // Redis'teki gibi kesin sayaç değil, map istatistiklerinden türetildi
            ce.setOverallStatus(MetricStatus.ESTIMATED);
            ce.setHitRatioStatus(totalGets > 0
                    ? MetricStatus.ESTIMATED : MetricStatus.NOT_APPLICABLE);
            ce.setThroughputStatus(MetricStatus.NOT_APPLICABLE);
            ce.setNetworkStatus(MetricStatus.NOT_APPLICABLE);
            return ce;

        } catch (Exception e) {
            logger.error("Error fetching cache efficiency for {}: {}",
                    instance.getName(), e.getMessage());
            CacheEfficiencyStats down = new CacheEfficiencyStats(0, 0);
            down.setOverallStatus(MetricStatus.UNAVAILABLE);
            down.setHitRatioStatus(MetricStatus.UNAVAILABLE);
            return down;
        }
    }

    @Override
    public ReplicationInfo getReplicationInfo(InstanceRef instance) {
        return ReplicationInfo.notApplicable();
    }

    /** Hazelcast persistence (Hot Restart) enterprise özelliğidir. */
    @Override
    public PersistenceInfo getPersistenceInfo(InstanceRef instance) {
        return PersistenceInfo.notApplicable();
    }

    @Override
    public List<KeyPatternStats> getKeyPatternStats(InstanceRef instance,
                                                    List<KeyPatternConfig.PatternDef> patterns) {
        // Hazelcast'te "key" = IMap adı. Map'leri desenlere göre grupla.
        List<KeyPatternStats> result = new ArrayList<>();
        for (KeyPatternConfig.PatternDef def : patterns) {
            result.add(new KeyPatternStats(def.getPattern(), def.getLabel()));
        }
        KeyPatternStats other = new KeyPatternStats("*", "Diğer");

        try {
            HazelcastInstance client = connectionManager.getClient(instance);
            if (client == null) {
                result.removeIf(s -> s.getKeyCount() == 0);
                return result;
            }

            client.getDistributedObjects().stream()
                    .filter(obj -> obj instanceof com.hazelcast.map.IMap)
                    .forEach(obj -> {
                        String mapName = obj.getName();
                        long entryCount = client.getMap(mapName).size();

                        boolean matched = false;
                        for (KeyPatternStats stats : result) {
                            if (matchesPatternHz(mapName, stats.getPattern())) {
                                stats.increment(entryCount);   // boyut yerine entry sayısı
                                matched = true;
                                break;
                            }
                        }
                        if (!matched) {
                            other.increment(entryCount);
                        }
                    });
        } catch (Exception e) {
            logger.error("Error scanning key patterns for {}: {}",
                    instance.getName(), e.getMessage(), e);
        }

        if (other.getKeyCount() > 0) {
            result.add(other);
        }
        result.removeIf(s -> s.getKeyCount() == 0);
        return result;
    }

    private boolean matchesPatternHz(String mapName, String pattern) {

        return matchesPattern(mapName, pattern);
    }

    private boolean matchesPattern(String keyName, String pattern) {
        if (pattern == null || pattern.equals("*")) return true;
        StringBuilder regex = new StringBuilder("^");
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-?^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        return keyName.matches(regex.toString());
    }


}
