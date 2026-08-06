
package com.nkolay.ucmd.model.dto;

public class ResourceStats {

    private long memoryRssBytes;   // İşletim sisteminin gördüğü gerçek bellek
    public long getMemoryRssBytes() { return memoryRssBytes; }
    public void setMemoryRssBytes(long memoryRssBytes) { this.memoryRssBytes = memoryRssBytes; }
    private double cpuUsagePercent;
    private long memoryUsedBytes;
    private long memoryMaxBytes;
    private double memoryUsagePercent;
    private double fragmentationRatio;

    private long fragmentationBytes;

    private MetricMeta meta = new MetricMeta();

    private MetricStatus overallStatus;
    private MetricStatus cpuStats;
    private MetricStatus memoryStatus;
    private MetricStatus fragmentationStatus;

    public ResourceStats() {}

    public ResourceStats(double cpuUsagePercent, long memoryUsedBytes, long memoryMaxBytes) {
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryMaxBytes = memoryMaxBytes;
        this.memoryUsagePercent = memoryMaxBytes > 0 ? (double) memoryUsedBytes / memoryMaxBytes * 100 : 0;
        
    }

    public double getCpuUsagePercent() { return cpuUsagePercent; }
    public void setCpuUsagePercent(double cpuUsagePercent) { this.cpuUsagePercent = cpuUsagePercent; }

    public long getMemoryUsedBytes() { return memoryUsedBytes; }
    public void setMemoryUsedBytes(long memoryUsedBytes) { this.memoryUsedBytes = memoryUsedBytes; }

    public long getMemoryMaxBytes() { return memoryMaxBytes; }
    public void setMemoryMaxBytes(long memoryMaxBytes) { this.memoryMaxBytes = memoryMaxBytes; }

    public double getMemoryUsagePercent() { return memoryUsagePercent; }
    public void setMemoryUsagePercent(double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }

    public double getFragmentationRatio() { return fragmentationRatio; }
    public void setFragmentationRatio(double fragmentationRatio) { this.fragmentationRatio = fragmentationRatio; }

    public long getFragmentationBytes() { return fragmentationBytes; }
    public void setFragmentationBytes(long v) { this.fragmentationBytes = v; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus overallStatus) { this.overallStatus = overallStatus; }

    public MetricStatus getCpuStats() { return cpuStats; }
    public void setCpuStats(MetricStatus cpuStats) { this.cpuStats = cpuStats; }

    public MetricStatus getMemoryStatus() { return memoryStatus; }
    public void setMemoryStatus(MetricStatus memoryStatus) { this.memoryStatus = memoryStatus; }

    public MetricStatus getFragmentationStatus() { return fragmentationStatus; }
    public void setFragmentationStatus(MetricStatus fragmentationStatus) { this.fragmentationStatus = fragmentationStatus; }

    public MetricMeta getMeta() {return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}
}
 