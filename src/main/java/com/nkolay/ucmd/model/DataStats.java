
package com.nkolay.ucmd.model;

public class DataStats {
    private long totalKeyCount;
    private long usedMemoryBytes;
    private long maxMemoryBytes;
    private double memoryUsagePercent;
    private double fragmentationRatio;

    public DataStats() {}

    public DataStats(long totalKeyCount, long usedMemoryBytes, long maxMemoryBytes) {
        this.totalKeyCount = totalKeyCount;
        this.usedMemoryBytes = usedMemoryBytes;
        this.maxMemoryBytes = maxMemoryBytes;
        this.memoryUsagePercent = maxMemoryBytes > 0 ? (double) usedMemoryBytes / maxMemoryBytes * 100 : 0;
    }

    public long getTotalKeyCount() { return totalKeyCount; }
    public void setTotalKeyCount(long totalKeyCount) { this.totalKeyCount = totalKeyCount; }

    public long getUsedMemoryBytes() { return usedMemoryBytes; }
    public void setUsedMemoryBytes(long usedMemoryBytes) { this.usedMemoryBytes = usedMemoryBytes; }

    public long getMaxMemoryBytes() { return maxMemoryBytes; }
    public void setMaxMemoryBytes(long maxMemoryBytes) { this.maxMemoryBytes = maxMemoryBytes; }

    public double getMemoryUsagePercent() { return memoryUsagePercent; }
    public void setMemoryUsagePercent(double memoryUsagePercent) { this.memoryUsagePercent = memoryUsagePercent; }

    public double getFragmentationRatio() { return fragmentationRatio; }
    public void setFragmentationRatio(double fragmentationRatio) { this.fragmentationRatio = fragmentationRatio; }
}
 