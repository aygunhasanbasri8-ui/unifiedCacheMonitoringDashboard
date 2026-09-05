package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.TtlStatus;

public class KeyTtlResponse {
    private String key;
    private long ttlMillis;
    private String ttlDisplay;
    private TtlStatus ttlStatus;
    private MetricMeta meta = new MetricMeta();

    public KeyTtlResponse() {}

    public KeyTtlResponse(String key, long ttlMillis) {
        this(key, ttlMillis, resolveStatus(ttlMillis));
    }

    public KeyTtlResponse(String key, long ttlMillis, TtlStatus ttlStatus) {
        this.key = key;
        this.ttlMillis = ttlMillis;
        this.ttlStatus = ttlStatus;
        this.ttlDisplay = toDisplay(ttlStatus, ttlMillis);
    }

    public static KeyTtlResponse unavailable(String key) {
        return new KeyTtlResponse(key, -2, TtlStatus.UNAVAILABLE);
    }

    private static TtlStatus resolveStatus(long ttl) {
        if (ttl == -1) return TtlStatus.PERSISTENT;
        if (ttl == -2) return TtlStatus.NOT_FOUND;
        return TtlStatus.EXPIRING;
    }

    private String toDisplay(TtlStatus status, long ttl) {
        return switch (status) {
            case PERSISTENT -> "No expiration";
            case NOT_FOUND -> "Key doesn't exist";
            case UNAVAILABLE -> "Unavailable";
            case EXPIRING -> ttl / 1000 + " seconds";
        };
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public long getTtlMillis() { return ttlMillis; }
    public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }

    public String getTtlDisplay() { return ttlDisplay; }
    public void setTtlDisplay(String ttlDisplay) { this.ttlDisplay = ttlDisplay; }

    public TtlStatus getTtlStatus() {return ttlStatus;}
    public void setTtlStatus(TtlStatus ttlStatus) {this.ttlStatus = ttlStatus;}

    public MetricMeta getMeta() {return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}

}
