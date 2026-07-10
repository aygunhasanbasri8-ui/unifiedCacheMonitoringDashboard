
package com.nkolay.ucmd.model;

public class ConnectionStats {
    private int activeClients;
    private long totalConnectionErrors;
    private long rejectedConnections;

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
}
 