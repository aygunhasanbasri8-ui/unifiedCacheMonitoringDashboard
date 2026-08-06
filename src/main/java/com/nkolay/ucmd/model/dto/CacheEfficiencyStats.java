package com.nkolay.ucmd.model.dto;

public class CacheEfficiencyStats {
    private long keyspaceHits;
    private long keyspaceMisses;

    private Double hitRatio;
    private Double missRatio;

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

    public double getHitRatio() { return hitRatio; }
    public void setHitRatio(double hitRatio) { this.hitRatio = hitRatio; }

    public double getMissRatio() { return missRatio; }
    public void setMissRatio(double missRatio) { this.missRatio = missRatio; }

    public MetricStatus getOverallStatus() {return overallStatus;}
    public void setOverallStatus(MetricStatus overallStatus) {this.overallStatus = overallStatus;}

    public MetricStatus getHitRatioStatus() {return hitRatioStatus;}
    public void setHitRatioStatus(MetricStatus hitRatioStatus) {this.hitRatioStatus = hitRatioStatus;}

    public MetricMeta getMeta() { return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}
}
