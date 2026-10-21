package com.ucmd.model.dto;

public class KeyPatternStats {
    private String pattern;        // örn "ucmd:merchant:*"
    private String label;
    private long keyCount;
    private long totalSizeBytes;

    public KeyPatternStats() {}

    public KeyPatternStats(String pattern, String label) {
        this.pattern = pattern;
        this.label = label;
        this.keyCount = 0;
        this.totalSizeBytes = 0;
    }

    public void increment(long sizeBytes) {
        this.keyCount++;
        this.totalSizeBytes += sizeBytes;
    }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public long getKeyCount() { return keyCount; }
    public void setKeyCount(long keyCount) { this.keyCount = keyCount; }

    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
}