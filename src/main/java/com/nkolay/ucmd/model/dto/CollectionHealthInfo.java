package com.nkolay.ucmd.model.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Rehber §6 — UCMD'nin kendi toplama kalitesi özeti. */
public class CollectionHealthInfo {

    private Instant collectedAt;
    private long collectionDurationMs;
    private String status;              // OK | PARTIAL | FAILED
    private boolean stale;
    private long staleAgeSeconds;
    private List<String> unavailableInstances = new ArrayList<>();
    private int totalInstances;
    private int healthyInstances;

    public CollectionHealthInfo() {}

    public Instant getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Instant collectedAt) { this.collectedAt = collectedAt; }

    public long getCollectionDurationMs() { return collectionDurationMs; }
    public void setCollectionDurationMs(long collectionDurationMs) {
        this.collectionDurationMs = collectionDurationMs;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }

    public long getStaleAgeSeconds() { return staleAgeSeconds; }
    public void setStaleAgeSeconds(long staleAgeSeconds) { this.staleAgeSeconds = staleAgeSeconds; }

    public List<String> getUnavailableInstances() { return unavailableInstances; }
    public void setUnavailableInstances(List<String> unavailableInstances) {
        this.unavailableInstances = unavailableInstances;
    }

    public int getTotalInstances() { return totalInstances; }
    public void setTotalInstances(int totalInstances) { this.totalInstances = totalInstances; }

    public int getHealthyInstances() { return healthyInstances; }
    public void setHealthyInstances(int healthyInstances) { this.healthyInstances = healthyInstances; }
}
