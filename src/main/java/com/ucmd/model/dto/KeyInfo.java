package com.ucmd.model.dto;

import com.ucmd.enums.MetricStatus;
import com.ucmd.enums.TtlStatus;

public class KeyInfo {
    private String keyName;
    private String keyType;        // string, hash, list, set, zset
    private long sizeBytes;
    private long ttlMillis;        // -1 = no expire, -2 = key doesn't exist
    /** Hazelcast IMap'lerinde entry sayısı; Redis'te null. */
    private Long entryCount;
    private TtlStatus ttlStatus;
    private MetricStatus sizeStatus;
    private String keyRef;
    public KeyInfo() {}

    public KeyInfo(String keyName, String keyType, long sizeBytes, long ttlMillis) {
        this.keyName = keyName;
        this.keyType = keyType;
        this.sizeBytes = sizeBytes;
        this.ttlMillis = ttlMillis;
        this.ttlStatus = (ttlMillis == -1) ? TtlStatus.PERSISTENT
                : (ttlMillis == -2) ? TtlStatus.NOT_FOUND : TtlStatus.EXPIRING;
    }


    public String getKeyRef() {return keyRef;}
    public void setKeyRef(String keyRef) {this.keyRef = keyRef;}

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public long getTtlMillis() { return ttlMillis; }
    public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }
    
    public Long getEntryCount() { return entryCount; }
    public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }

    public TtlStatus getTtlStatus() {return ttlStatus;}
    public void setTtlStatus(TtlStatus ttlStatus) {this.ttlStatus = ttlStatus;}

    public String getTtlDisplay() {
        if (ttlStatus == null) return "Unknown";
        return switch (ttlStatus) {
            case PERSISTENT -> "No expiration";
            case NOT_FOUND -> "Key doesn't exist";
            case UNAVAILABLE -> "Unavailable";
            case EXPIRING -> ttlMillis / 1000 + " seconds";
        };
    }

    public MetricStatus getSizeStatus() { return sizeStatus;}
    public void setSizeStatus(MetricStatus sizeStatus) { this.sizeStatus = sizeStatus;}
    
}


 