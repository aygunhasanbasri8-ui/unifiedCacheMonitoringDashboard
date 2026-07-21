package com.nkolay.ucmd.model.dto;

public class CacheEfficiencyStats {
    private long keyspaceHits;
    private long keyspaceMisses;
    private double hitRatio;
    private double missRatio;

    public CacheEfficiencyStats() {}
    public CacheEfficiencyStats(long keyspaceHits, long keyspaceMisses) {
        this.keyspaceHits = keyspaceHits;
        this.keyspaceMisses = keyspaceMisses;
        long total = keyspaceHits + keyspaceMisses;
        this.hitRatio = total > 0 ? (double) keyspaceHits / total : 0.0;
        this.missRatio = total > 0 ? (double) keyspaceMisses / total : 0.0;
    }

    public long getKeyspaceHits() { return keyspaceHits; }
    public void setKeyspaceHits(long keyspaceHits) { this.keyspaceHits = keyspaceHits; }

    public long getKeyspaceMisses() { return keyspaceMisses; }
    public void setKeyspaceMisses(long keyspaceMisses) { this.keyspaceMisses = keyspaceMisses; }

    public double getHitRatio() { return hitRatio; }
    public void setHitRatio(double hitRatio) { this.hitRatio = hitRatio; }

    public double getMissRatio() { return missRatio; }
    public void setMissRatio(double missRatio) { this.missRatio = missRatio; }

}
