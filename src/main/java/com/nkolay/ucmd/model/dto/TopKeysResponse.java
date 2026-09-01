package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;

import java.util.ArrayList;
import java.util.List;

public class TopKeysResponse {

    private List<KeyInfo> keys = new ArrayList<>();
    private boolean estimated;
    private long scannedCount;
    private long scanLimit;
    private boolean scanCompleted;
    private MetricStatus status = MetricStatus.AVAILABLE;
    private MetricMeta meta = new MetricMeta();

    public TopKeysResponse() {}

    public TopKeysResponse(List<KeyInfo> keys, long scannedCount, long scanLimit, boolean scanCompleted) {
        this.keys = keys != null ? keys : new ArrayList<>();
        this.scannedCount = scannedCount;
        this.scanLimit = scanLimit;
        this.scanCompleted = scanCompleted;
        this.estimated = !scanCompleted;
        this.status = scanCompleted ? MetricStatus.AVAILABLE : MetricStatus.ESTIMATED;
    }

    public static TopKeysResponse unavailable() {
        TopKeysResponse r = new TopKeysResponse();
        r.setStatus(MetricStatus.UNAVAILABLE);
        return r;
    }

    public List<KeyInfo> getKeys() { return keys; }
    public void setKeys(List<KeyInfo> keys) { this.keys = keys; }

    public boolean isEstimated() { return estimated; }
    public void setEstimated(boolean estimated) { this.estimated = estimated; }

    public long getScannedCount() { return scannedCount; }
    public void setScannedCount(long scannedCount) { this.scannedCount = scannedCount; }

    public long getScanLimit() { return scanLimit; }
    public void setScanLimit(long scanLimit) { this.scanLimit = scanLimit; }

    public boolean isScanCompleted() { return scanCompleted; }
    public void setScanCompleted(boolean scanCompleted) { this.scanCompleted = scanCompleted; }

    public MetricStatus getStatus() { return status; }
    public void setStatus(MetricStatus status) { this.status = status; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }

}
