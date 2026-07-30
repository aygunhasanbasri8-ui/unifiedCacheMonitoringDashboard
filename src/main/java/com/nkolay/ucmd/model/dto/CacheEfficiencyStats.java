package com.nkolay.ucmd.model.dto;

public class CacheEfficiencyStats {
    private long keyspaceHits;
    private long keyspaceMisses;
    private double hitRatio;
    private double missRatio;
    public boolean hasTraffic() {
        return (keyspaceHits + keyspaceMisses) > 0;
    }

    private MetricStatus overallStatus;

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
            this.hitRatio = Double.NaN;
            this.missRatio = Double.NaN;
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
}
