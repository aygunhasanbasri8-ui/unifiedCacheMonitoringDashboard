package com.nkolay.ucmd.model.dto;

public class CacheEfficiencyStats {
    private long keyspaceHits;
    private long keyspaceMisses;

    private Double hitRatio;
    private Double missRatio;

    private long instantaneousOpsPerSec;
    private long totalCommandsProcessed;
    private double inputKbps;
    private double outputKbps;
    private long totalErrorReplies;

    private MetricStatus throughputStatus;
    private MetricStatus networkStatus;

    private MetricStatus overallStatus;
    private MetricStatus hitRatioStatus;
    private MetricMeta meta = new MetricMeta();


    public CacheEfficiencyStats() {}

    public CacheEfficiencyStats(long keyspaceHits, long keyspaceMisses) {
        this.keyspaceHits = keyspaceHits;
        this.keyspaceMisses = keyspaceMisses;
        long total = keyspaceHits + keyspaceMisses;
        if (total > 0) {
            this.hitRatio = (double) keyspaceHits / total;
            this.missRatio = (double) keyspaceMisses / total;
            this.overallStatus = MetricStatus.AVAILABLE;
        } else {
            this.hitRatio = null;
            this.missRatio = null;
            this.overallStatus = MetricStatus.NOT_APPLICABLE;
        }
    }

    public long getKeyspaceHits() { return keyspaceHits; }
    public void setKeyspaceHits(long keyspaceHits) { this.keyspaceHits = keyspaceHits; }

    public long getKeyspaceMisses() { return keyspaceMisses; }
    public void setKeyspaceMisses(long keyspaceMisses) { this.keyspaceMisses = keyspaceMisses; }

    public Double getHitRatio() { return hitRatio; }
    public void setHitRatio(Double hitRatio) { this.hitRatio = hitRatio; }

    public Double getMissRatio() { return missRatio; }
    public void setMissRatio(Double missRatio) { this.missRatio = missRatio; }

    public MetricStatus getOverallStatus() {return overallStatus;}
    public void setOverallStatus(MetricStatus overallStatus) {this.overallStatus = overallStatus;}

    public MetricStatus getHitRatioStatus() {return hitRatioStatus;}
    public void setHitRatioStatus(MetricStatus hitRatioStatus) {this.hitRatioStatus = hitRatioStatus;}

    public MetricMeta getMeta() { return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}

    public long getInstantaneousOpsPerSec() { return instantaneousOpsPerSec; }
    public void setInstantaneousOpsPerSec(long v) { this.instantaneousOpsPerSec = v; }

    public long getTotalCommandsProcessed() { return totalCommandsProcessed; }
    public void setTotalCommandsProcessed(long v) { this.totalCommandsProcessed = v; }

    public double getInputKbps() { return inputKbps; }
    public void setInputKbps(double v) { this.inputKbps = v; }

    public double getOutputKbps() { return outputKbps; }
    public void setOutputKbps(double v) { this.outputKbps = v; }

    public long getTotalErrorReplies() { return totalErrorReplies; }
    public void setTotalErrorReplies(long v) { this.totalErrorReplies = v; }

    public MetricStatus getThroughputStatus() { return throughputStatus; }
    public void setThroughputStatus(MetricStatus v) { this.throughputStatus = v; }

    public MetricStatus getNetworkStatus() { return networkStatus; }
    public void setNetworkStatus(MetricStatus v) { this.networkStatus = v; }
}
