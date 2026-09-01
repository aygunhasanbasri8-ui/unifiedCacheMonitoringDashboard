
package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;

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

    // ── Rehber §5.6 — JVM metrikleri (Hazelcast; Redis'te null) ──
    private Double systemCpuPercent;
    private Double systemLoadAverage;
    private Long nonHeapUsedBytes;
    private Long nonHeapMaxBytes;
    private Long gcCollectionCount;
    private Double gcCollectionSeconds;
    private Integer threadCount;
    private Integer peakThreadCount;
    private Integer daemonThreadCount;
    private Integer deadlockedThreadCount;
    private Long openFileDescriptors;
    private Long maxFileDescriptors;
    private Long loadedClassCount;
    private Long memberUptimeSeconds;

    private MetricStatus jvmStatus;
    private MetricStatus gcStatus;
    private MetricStatus threadStatus;
    private MetricStatus fileDescriptorStatus;

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

    // ── Rehber §5.6 — JVM metrikleri ──

    public Double getSystemCpuPercent() { return systemCpuPercent; }
    public void setSystemCpuPercent(Double v) { this.systemCpuPercent = v; }

    public Double getSystemLoadAverage() { return systemLoadAverage; }
    public void setSystemLoadAverage(Double v) { this.systemLoadAverage = v; }

    public Long getNonHeapUsedBytes() { return nonHeapUsedBytes; }
    public void setNonHeapUsedBytes(Long v) { this.nonHeapUsedBytes = v; }

    public Long getNonHeapMaxBytes() { return nonHeapMaxBytes; }
    public void setNonHeapMaxBytes(Long v) { this.nonHeapMaxBytes = v; }

    public Long getGcCollectionCount() { return gcCollectionCount; }
    public void setGcCollectionCount(Long v) { this.gcCollectionCount = v; }

    public Double getGcCollectionSeconds() { return gcCollectionSeconds; }
    public void setGcCollectionSeconds(Double v) { this.gcCollectionSeconds = v; }

    public Integer getThreadCount() { return threadCount; }
    public void setThreadCount(Integer v) { this.threadCount = v; }

    public Integer getPeakThreadCount() { return peakThreadCount; }
    public void setPeakThreadCount(Integer v) { this.peakThreadCount = v; }

    public Integer getDaemonThreadCount() { return daemonThreadCount; }
    public void setDaemonThreadCount(Integer v) { this.daemonThreadCount = v; }

    public Integer getDeadlockedThreadCount() { return deadlockedThreadCount; }
    public void setDeadlockedThreadCount(Integer v) { this.deadlockedThreadCount = v; }

    public Long getOpenFileDescriptors() { return openFileDescriptors; }
    public void setOpenFileDescriptors(Long v) { this.openFileDescriptors = v; }

    public Long getMaxFileDescriptors() { return maxFileDescriptors; }
    public void setMaxFileDescriptors(Long v) { this.maxFileDescriptors = v; }

    public Long getLoadedClassCount() { return loadedClassCount; }
    public void setLoadedClassCount(Long v) { this.loadedClassCount = v; }

    public Long getMemberUptimeSeconds() { return memberUptimeSeconds; }
    public void setMemberUptimeSeconds(Long v) { this.memberUptimeSeconds = v; }

    public MetricStatus getJvmStatus() { return jvmStatus; }
    public void setJvmStatus(MetricStatus v) { this.jvmStatus = v; }

    public MetricStatus getGcStatus() { return gcStatus; }
    public void setGcStatus(MetricStatus v) { this.gcStatus = v; }

    public MetricStatus getThreadStatus() { return threadStatus; }
    public void setThreadStatus(MetricStatus v) { this.threadStatus = v; }

    public MetricStatus getFileDescriptorStatus() { return fileDescriptorStatus; }
    public void setFileDescriptorStatus(MetricStatus v) { this.fileDescriptorStatus = v; }

    /** FD limitine yaklaşma oranı. Limit yoksa null. */
    public Double getFileDescriptorUsagePercent() {
        if (maxFileDescriptors == null || maxFileDescriptors <= 0
                || openFileDescriptors == null) return null;
        return (double) openFileDescriptors / maxFileDescriptors * 100;
    }
}
 