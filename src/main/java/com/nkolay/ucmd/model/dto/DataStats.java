
package com.nkolay.ucmd.model.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataStats {
    private long totalKeyCount;
    private long usedMemoryBytes;
    private long maxMemoryBytes;
    private Double memoryUsagePercent;
    private double fragmentationRatio;

    private long evictedKeys;
    private long expiredKeys;
    private long keysWithExpiry;
    private Long avgTtlMillis;                    // null = N/A
    private long fragmentationBytes;
    private Map<String, Long> keysPerDatabase = new LinkedHashMap<>();

    private MetricStatus evictionStatus;
    private MetricStatus ttlStatus;
    private MetricStatus overallStatus;
    private MetricStatus memoryStatus;
    private MetricStatus fragmentationStatus;
    private MetricMeta meta = new MetricMeta();

    public DataStats() {}

    public DataStats(long totalKeyCount, long usedMemoryBytes, long maxMemoryBytes) {
        this.totalKeyCount = totalKeyCount;
        this.usedMemoryBytes = usedMemoryBytes;
        this.maxMemoryBytes = maxMemoryBytes;
        if (maxMemoryBytes > 0) {
            this.memoryUsagePercent = (double) usedMemoryBytes / maxMemoryBytes * 100;
            this.memoryStatus = MetricStatus.AVAILABLE;
        } else {
            this.memoryUsagePercent = null;
            this.memoryStatus = MetricStatus.NOT_APPLICABLE;
        }
    }

    public long getTotalKeyCount() { return totalKeyCount; }
    public void setTotalKeyCount(long totalKeyCount) { this.totalKeyCount = totalKeyCount; }

    public long getUsedMemoryBytes() { return usedMemoryBytes; }
    public void setUsedMemoryBytes(long usedMemoryBytes) { this.usedMemoryBytes = usedMemoryBytes; }

    public long getMaxMemoryBytes() { return maxMemoryBytes; }
    public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }

    public Double getMemoryUsagePercent() { return memoryUsagePercent; }
    public void setMemoryUsagePercent(double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }

    public double getFragmentationRatio() { return fragmentationRatio; }
    public void setFragmentationRatio(double fragmentationRatio) { this.fragmentationRatio = fragmentationRatio; }

    public MetricStatus getOverallStatus() {return overallStatus;}
    public void setOverallStatus(MetricStatus overallStatus) { this.overallStatus = overallStatus;}

    public MetricStatus getMemoryStatus() {return memoryStatus;}
    public void setMemoryStatus(MetricStatus memoryStatus) {this.memoryStatus = memoryStatus;}

    public MetricStatus getFragmentationStatus() { return fragmentationStatus;}
    public void setFragmentationStatus(MetricStatus fragmentationStatus) { this.fragmentationStatus = fragmentationStatus;}

    public MetricMeta getMeta() {return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}

    public long getEvictedKeys() { return evictedKeys; }
    public void setEvictedKeys(long evictedKeys) { this.evictedKeys = evictedKeys; }

    public long getExpiredKeys() { return expiredKeys; }
    public void setExpiredKeys(long expiredKeys) { this.expiredKeys = expiredKeys; }

    public long getKeysWithExpiry() { return keysWithExpiry; }
    public void setKeysWithExpiry(long keysWithExpiry) { this.keysWithExpiry = keysWithExpiry; }

    public Long getAvgTtlMillis() { return avgTtlMillis; }
    public void setAvgTtlMillis(Long avgTtlMillis) { this.avgTtlMillis = avgTtlMillis; }

    public long getFragmentationBytes() { return fragmentationBytes; }
    public void setFragmentationBytes(long v) { this.fragmentationBytes = v; }

    public Map<String, Long> getKeysPerDatabase() { return keysPerDatabase; }
    public void setKeysPerDatabase(Map<String, Long> v) { this.keysPerDatabase = v; }

    public MetricStatus getEvictionStatus() { return evictionStatus; }
    public void setEvictionStatus(MetricStatus v) { this.evictionStatus = v; }

    public MetricStatus getTtlStatus() { return ttlStatus; }
    public void setTtlStatus(MetricStatus v) { this.ttlStatus = v; }

}
 