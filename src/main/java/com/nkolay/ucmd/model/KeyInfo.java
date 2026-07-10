package com.nkolay.ucmd.model;

public class KeyInfo {
    private String keyName;
    private String keyType;        // string, hash, list, set, zset
    private long sizeBytes;
    private long ttlMillis;        // -1 = no expire, -2 = key doesn't exist
    private long lastAccessTime;
    private long accessCount;

    public KeyInfo() {}

    public KeyInfo(String keyName, String keyType, long sizeBytes, long ttlMillis) {
        this.keyName = keyName;
        this.keyType = keyType;
        this.sizeBytes = sizeBytes;
        this.ttlMillis = ttlMillis;
        this.lastAccessTime = System.currentTimeMillis();
    }

    public String getKeyName() { return keyName; }
    public void setKeyName(String keyName) { this.keyName = keyName; }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }

    public long getTtlMillis() { return ttlMillis; }
    public void setTtlMillis(long ttlMillis) { this.ttlMillis = ttlMillis; }

    public long getLastAccessTime() { return lastAccessTime; }
    public void setLastAccessTime(long lastAccessTime) { this.lastAccessTime = lastAccessTime; }

    public long getAccessCount() { return accessCount; }
    public void setAccessCount(long accessCount) { this.accessCount = accessCount; }

    public String getTtlDisplay() {
        if (ttlMillis == -1) return "No expiration";
        if (ttlMillis == -2) return "Key doesn't exist";
        return ttlMillis / 1000 + " seconds";
    }
}