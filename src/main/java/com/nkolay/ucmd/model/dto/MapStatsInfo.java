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
        private Long ownedEntryMemoryCost;   // null = client-only'de alınamaz
        private Long backupEntryCount;
        private Long hits;
        private MetricStatus entryCountStatus;

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
    }
}
