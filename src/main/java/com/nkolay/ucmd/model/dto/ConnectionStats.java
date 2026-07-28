
package com.nkolay.ucmd.model.dto;

public class ConnectionStats {
    private int activeClients;
    private long totalConnectionErrors;
    private long rejectedConnections;

    private MetricStatus overallStatus;
    private MetricStatus activeClientStatus;
    private MetricStatus errorStatus;

    public ConnectionStats() {}

    public ConnectionStats(int activeClients, long totalConnectionErrors, long rejectedConnections) {
        this.activeClients = activeClients;
        this.totalConnectionErrors = totalConnectionErrors;
        this.rejectedConnections = rejectedConnections;
    }

    public int getActiveClients() { return activeClients; }
    public void setActiveClients(int activeClients) { this.activeClients = activeClients; }

    public long getTotalConnectionErrors() { return totalConnectionErrors; }
    public void setTotalConnectionErrors(long totalConnectionErrors) { this.totalConnectionErrors = totalConnectionErrors; }

    public long getRejectedConnections() { return rejectedConnections; }
    public void setRejectedConnections(long rejectedConnections) { this.rejectedConnections = rejectedConnections; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus overallStatus) { this.overallStatus = overallStatus; }

    public MetricStatus getActiveClientStatus() { return activeClientStatus; }
    public void setActiveClientStatus(MetricStatus activeClientStatus) { this.activeClientStatus = activeClientStatus; }

    public MetricStatus getErrorStatus() { return errorStatus; }
    public void setErrorStatus(MetricStatus errorStatus) { this.errorStatus = errorStatus; }
}
 