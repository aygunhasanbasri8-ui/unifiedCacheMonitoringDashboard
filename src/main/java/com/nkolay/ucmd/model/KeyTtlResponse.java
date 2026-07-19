package com.nkolay.ucmd.model;

public class KeyTtlResponse {
    private String key;
    private long ttlMillis;
    private String ttlDisplay;

    public KeyTtlResponse() {}

    public KeyTtlResponse(String key, long ttlMillis) {
        this.key = key;
        this.ttlMillis = ttlMillis;
        this.ttlDisplay = toDisplay(ttlMillis);
    }

    private String toDisplay(long ttl) {
        if (ttl == -1) return "No expiration";
        if (ttl == -2) return "Key doesn't exist";
        return ttl / 1000 + " seconds";
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public long getTtlMillis() { return ttlMillis; }
    public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }

    public String getTtlDisplay() { return ttlDisplay; }
    public void setTtlDisplay(String ttlDisplay) { this.ttlDisplay = ttlDisplay; }
}
