
package com.nkolay.ucmd.model.dto;

public class ResourceStats {
    private double cpuUsagePercent;
    private long memoryUsedBytes;
    private long memoryMaxBytes;
    private double memoryUsagePercent;
    private double fragmentationRatio;

    public ResourceStats() {}

    public ResourceStats(double cpuUsagePercent, long memoryUsedBytes, long memoryMaxBytes) {
        this.cpuUsagePercent = cpuUsagePercent;
        this.memoryUsedBytes = memoryUsedBytes;
        this.memoryMaxBytes = memoryMaxBytes;
        this.memoryUsagePercent = memoryMaxBytes > 0 ? (double) memoryUsedBytes / memoryMaxBytes * 100 : 0;}

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
}
 