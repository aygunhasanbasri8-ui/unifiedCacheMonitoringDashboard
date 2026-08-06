package com.nkolay.ucmd.model.dto;

public class HealthInfo {
    private String status;      // UP | DOWN
    private String version;
    private long uptimeSeconds;
    private long timestamp;
    private MetricMeta meta = new MetricMeta();

    public HealthInfo() {}

    public HealthInfo(String status, String version, long uptimeSeconds) {
        this.status = status;
        this.version = version;
        this.uptimeSeconds = uptimeSeconds;
        this.timestamp = System.currentTimeMillis();
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public MetricMeta getMeta() {return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}

}
