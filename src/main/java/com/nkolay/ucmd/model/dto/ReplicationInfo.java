package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;

import java.util.ArrayList;
import java.util.List;


public class ReplicationInfo implements MonitoredMetric {

    private String role;                    // master | slave
    private int connectedReplicas;
    private String masterLinkStatus;        // up | down (yalnızca replica'da)
    private Long masterLastIoSecondsAgo;
    private Long replicaLagBytes;
    private long masterReplOffset;
    private boolean masterSyncInProgress;
    private List<ReplicaInfo> replicas = new ArrayList<>();

    private MetricStatus overallStatus;
    private MetricMeta meta = new MetricMeta();

    public ReplicationInfo() {}

    public static ReplicationInfo notApplicable() {
        ReplicationInfo r = new ReplicationInfo();
        r.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return r;
    }

    public static ReplicationInfo unavailable() {
        ReplicationInfo r = new ReplicationInfo();
        r.setOverallStatus(MetricStatus.UNAVAILABLE);
        return r;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getConnectedReplicas() { return connectedReplicas; }
    public void setConnectedReplicas(int v) { this.connectedReplicas = v; }

    public String getMasterLinkStatus() { return masterLinkStatus; }
    public void setMasterLinkStatus(String v) { this.masterLinkStatus = v; }

    public Long getMasterLastIoSecondsAgo() { return masterLastIoSecondsAgo; }
    public void setMasterLastIoSecondsAgo(Long v) { this.masterLastIoSecondsAgo = v; }

    public Long getReplicaLagBytes() { return replicaLagBytes; }
    public void setReplicaLagBytes(Long v) { this.replicaLagBytes = v; }

    public long getMasterReplOffset() { return masterReplOffset; }
    public void setMasterReplOffset(long v) { this.masterReplOffset = v; }

    public boolean isMasterSyncInProgress() { return masterSyncInProgress; }
    public void setMasterSyncInProgress(boolean v) { this.masterSyncInProgress = v; }

    public List<ReplicaInfo> getReplicas() { return replicas; }
    public void setReplicas(List<ReplicaInfo> v) { this.replicas = v; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus v) { this.overallStatus = v; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }

    /** slaveN:ip=...,port=...,state=online,offset=...,lag=0 */
    public static class ReplicaInfo {
        private String host;
        private int port;
        private String state;
        private long offset;
        private long lag;

        public ReplicaInfo() {}

        public ReplicaInfo(String host, int port, String state, long offset, long lag) {
            this.host = host;
            this.port = port;
            this.state = state;
            this.offset = offset;
            this.lag = lag;
        }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public String getState() { return state; }
        public void setState(String state) { this.state = state; }

        public long getOffset() { return offset; }
        public void setOffset(long offset) { this.offset = offset; }

        public long getLag() { return lag; }
        public void setLag(long lag) { this.lag = lag; }
    }
}