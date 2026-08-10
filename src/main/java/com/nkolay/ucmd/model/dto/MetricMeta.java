package com.nkolay.ucmd.model.dto;

import java.time.Instant;

public class MetricMeta {

    private Instant collectedAt;
    private long ageMillis;
    private boolean stale;

    public MetricMeta () {
        this.collectedAt = Instant.now();
        this.ageMillis = 0;
        this.stale = false;
    }

    public MetricMeta(Instant collectedAt, boolean stale) {
        this.collectedAt = collectedAt != null ? collectedAt :Instant.now();
        this.stale = stale;
        this.ageMillis = Math.max(0, System.currentTimeMillis() - this.collectedAt.toEpochMilli());
    }

    public Instant getCollectedAt() {return collectedAt;}
    public void setCollectedAt(Instant collectedAt) {this.collectedAt = collectedAt;}
    public boolean isStale() {return stale;}
    public void setStale(boolean stale) {this.stale = stale;}
    public long getAgeMillis() {return ageMillis;}
    public void setAgeMillis(long ageMillis) {this.ageMillis = ageMillis;}

}
