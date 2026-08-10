package com.nkolay.ucmd.model.dto;

public class ConnectionStats {
    private int activeClients;
    private long totalConnectionErrors;   // Redis'te doğrudan karşılığı yok
    private long rejectedConnections;

    // ── Rehber §4.2 eklemeleri ──
    private long maxClients;
    private int blockedClients;
    private long totalConnectionsReceived;

    private MetricStatus overallStatus;
    private MetricStatus activeClientStatus;
    private MetricStatus errorStatus;
    private MetricStatus maxClientsStatus;
    private MetricStatus blockedClientsStatus;

    private MetricMeta meta = new MetricMeta();

    public ConnectionStats() {}

    public ConnectionStats(int activeClients, long totalConnectionErrors, long rejectedConnections) {
        this.activeClients = activeClients;
        this.totalConnectionErrors = totalConnectionErrors;
        this.rejectedConnections = rejectedConnections;
    }

    /** Client limitine ne kadar yaklaşıldı — kapasite alarmı için. */
    public Double getClientUsagePercent() {
        if (maxClients <= 0) return null;   // N/A
        return (double) activeClients / maxClients * 100;
    }

    public int getActiveClients() { return activeClients; }
    public void setActiveClients(int activeClients) { this.activeClients = activeClients; }

    public long getTotalConnectionErrors() { return totalConnectionErrors; }
    public void setTotalConnectionErrors(long v) { this.totalConnectionErrors = v; }

    public long getRejectedConnections() { return rejectedConnections; }
    public void setRejectedConnections(long v) { this.rejectedConnections = v; }

    public long getMaxClients() { return maxClients; }
    public void setMaxClients(long maxClients) { this.maxClients = maxClients; }

    public int getBlockedClients() { return blockedClients; }
    public void setBlockedClients(int blockedClients) { this.blockedClients = blockedClients; }

    public long getTotalConnectionsReceived() { return totalConnectionsReceived; }
    public void setTotalConnectionsReceived(long v) { this.totalConnectionsReceived = v; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus v) { this.overallStatus = v; }

    public MetricStatus getActiveClientStatus() { return activeClientStatus; }
    public void setActiveClientStatus(MetricStatus v) { this.activeClientStatus = v; }

    public MetricStatus getErrorStatus() { return errorStatus; }
    public void setErrorStatus(MetricStatus v) { this.errorStatus = v; }

    public MetricStatus getMaxClientsStatus() { return maxClientsStatus; }
    public void setMaxClientsStatus(MetricStatus v) { this.maxClientsStatus = v; }

    public MetricStatus getBlockedClientsStatus() { return blockedClientsStatus; }
    public void setBlockedClientsStatus(MetricStatus v) { this.blockedClientsStatus = v; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }
}