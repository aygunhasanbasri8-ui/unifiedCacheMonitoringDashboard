package com.nkolay.ucmd.model.dto;

import java.util.ArrayList;
import java.util.List;

/** Rehber §5.3 — Hazelcast map metrikleri. */
public class MapStatsInfo {

    private int mapCount;
    private long totalEntryCount;
    private List<MapDetail> maps = new ArrayList<>();

    private MetricStatus overallStatus;
    private MetricStatus memoryCostStatus;
    private MetricStatus operationStatus;
    private MetricMeta meta = new MetricMeta();

    public MapStatsInfo() {}

    public static MapStatsInfo unavailable() {
        MapStatsInfo m = new MapStatsInfo();
        m.setOverallStatus(MetricStatus.UNAVAILABLE);
        return m;
    }

    public static MapStatsInfo notApplicable() {
        MapStatsInfo m = new MapStatsInfo();
        m.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return m;
    }

    public int getMapCount() { return mapCount; }
    public void setMapCount(int mapCount) { this.mapCount = mapCount; }

    public long getTotalEntryCount() { return totalEntryCount; }
    public void setTotalEntryCount(long totalEntryCount) { this.totalEntryCount = totalEntryCount; }

    public List<MapDetail> getMaps() { return maps; }
    public void setMaps(List<MapDetail> maps) { this.maps = maps; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus overallStatus) { this.overallStatus = overallStatus; }

    public MetricStatus getMemoryCostStatus() { return memoryCostStatus; }
    public void setMemoryCostStatus(MetricStatus memoryCostStatus) {
        this.memoryCostStatus = memoryCostStatus;
    }

    public MetricStatus getOperationStatus() { return operationStatus; }
    public void setOperationStatus(MetricStatus operationStatus) {
        this.operationStatus = operationStatus;
    }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }

    public static class MapDetail {
        private String name;
        private long entryCount;
        private Long ownedEntryMemoryCost;   // null = Prometheus scrape yoksa alınamaz
        private Long backupEntryCount;
        private Long hits;
        private MetricStatus entryCountStatus;

        // ── Rehber §5.3 — Prometheus scrape'ten gelen detaylar ──
        private Long ownedEntryCount;
        private Long backupEntryMemoryCost;
        private Long heapCost;
        private Long getCount;
        private Long putCount;
        private Long removeCount;
        private Long lockedEntryCount;
        private Long dirtyEntryCount;
        private Long evictionCount;
        private Long expirationCount;
        private Integer backupCount;
        private Double avgGetLatencyMillis;
        private Double avgPutLatencyMillis;
        private Long lastAccessTime;      // epoch millis, 0 = hiç erişilmedi
        private Long lastUpdateTime;
        private Double hitRatio;          // hits / getCount, getCount=0 ise null

        public MapDetail() {}

        public MapDetail(String name, long entryCount) {
            this.name = name;
            this.entryCount = entryCount;
            this.entryCountStatus = MetricStatus.AVAILABLE;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getEntryCount() { return entryCount; }
        public void setEntryCount(long entryCount) { this.entryCount = entryCount; }

        public Long getOwnedEntryMemoryCost() { return ownedEntryMemoryCost; }
        public void setOwnedEntryMemoryCost(Long ownedEntryMemoryCost) {
            this.ownedEntryMemoryCost = ownedEntryMemoryCost;
        }

        public Long getBackupEntryCount() { return backupEntryCount; }
        public void setBackupEntryCount(Long backupEntryCount) {
            this.backupEntryCount = backupEntryCount;
        }

        public Long getHits() { return hits; }
        public void setHits(Long hits) { this.hits = hits; }

        public MetricStatus getEntryCountStatus() { return entryCountStatus; }
        public void setEntryCountStatus(MetricStatus entryCountStatus) {
            this.entryCountStatus = entryCountStatus;
        }

        // ── Rehber §5.3 detayları ──

        public Long getOwnedEntryCount() { return ownedEntryCount; }
        public void setOwnedEntryCount(Long v) { this.ownedEntryCount = v; }

        public Long getBackupEntryMemoryCost() { return backupEntryMemoryCost; }
        public void setBackupEntryMemoryCost(Long v) { this.backupEntryMemoryCost = v; }

        public Long getHeapCost() { return heapCost; }
        public void setHeapCost(Long v) { this.heapCost = v; }

        public Long getGetCount() { return getCount; }
        public void setGetCount(Long v) { this.getCount = v; }

        public Long getPutCount() { return putCount; }
        public void setPutCount(Long v) { this.putCount = v; }

        public Long getRemoveCount() { return removeCount; }
        public void setRemoveCount(Long v) { this.removeCount = v; }

        public Long getLockedEntryCount() { return lockedEntryCount; }
        public void setLockedEntryCount(Long v) { this.lockedEntryCount = v; }

        public Long getDirtyEntryCount() { return dirtyEntryCount; }
        public void setDirtyEntryCount(Long v) { this.dirtyEntryCount = v; }

        public Long getEvictionCount() { return evictionCount; }
        public void setEvictionCount(Long v) { this.evictionCount = v; }

        public Long getExpirationCount() { return expirationCount; }
        public void setExpirationCount(Long v) { this.expirationCount = v; }

        public Integer getBackupCount() { return backupCount; }
        public void setBackupCount(Integer v) { this.backupCount = v; }

        public Double getAvgGetLatencyMillis() { return avgGetLatencyMillis; }
        public void setAvgGetLatencyMillis(Double v) { this.avgGetLatencyMillis = v; }

        public Double getAvgPutLatencyMillis() { return avgPutLatencyMillis; }
        public void setAvgPutLatencyMillis(Double v) { this.avgPutLatencyMillis = v; }

        public Long getLastAccessTime() { return lastAccessTime; }
        public void setLastAccessTime(Long v) { this.lastAccessTime = v; }

        public Long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(Long v) { this.lastUpdateTime = v; }

        public Double getHitRatio() { return hitRatio; }
        public void setHitRatio(Double v) { this.hitRatio = v; }
    }
}
