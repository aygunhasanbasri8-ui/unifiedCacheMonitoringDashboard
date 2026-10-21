
package com.ucmd.model.dto;

import com.ucmd.enums.MetricStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClusterInfo implements MonitoredMetric {
    private int nodeCount;
    private int partitionCount;
    private List<MemberInfo> members = new ArrayList<>();
    private String clusterName;
    private Boolean balanced;

    private Map<String, Integer> partitionDistribution = new LinkedHashMap<>();
    private Double distributionSkewPercent;   // null = hesaplanamadı
    private Boolean clusterSafe;              // null = client-only'de alınamadı
    private Integer unassignedPartitions;
    private MetricStatus distributionStatus;
    private MetricStatus safetyStatus;

    private MetricStatus overallStatus;
    private MetricStatus partitionCountStatus;
    private MetricStatus balancedStatus;
    private MetricMeta meta = new MetricMeta();

    public ClusterInfo() {}

    public ClusterInfo(int nodeCount, int partitionCount, String clusterName) {
        this.nodeCount = nodeCount;
        this.partitionCount = partitionCount;
        this.clusterName = clusterName;
    }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }

    public int getPartitionCount() { return partitionCount; }
    public void setPartitionCount(int partitionCount) { this.partitionCount = partitionCount; }

    public List<MemberInfo> getMembers() { return members; }
    public void setMembers(List<MemberInfo> members) { this.members = members; }

    public String getClusterName() { return clusterName; }
    public void setClusterName(String clusterName) { this.clusterName = clusterName; }

    public Boolean getBalanced() { return balanced; }
    public void setBalanced(Boolean balanced) { this.balanced = balanced; }

    public Map<String, Integer> getPartitionDistribution() { return partitionDistribution; }
    public void setPartitionDistribution(Map<String, Integer> partitionDistribution) {
        this.partitionDistribution = partitionDistribution;
    }

    public Double getDistributionSkewPercent() { return distributionSkewPercent; }
    public void setDistributionSkewPercent(Double distributionSkewPercent) {
        this.distributionSkewPercent = distributionSkewPercent;
    }

    public Boolean getClusterSafe() { return clusterSafe; }
    public void setClusterSafe(Boolean clusterSafe) { this.clusterSafe = clusterSafe; }

    public Integer getUnassignedPartitions() { return unassignedPartitions; }
    public void setUnassignedPartitions(Integer unassignedPartitions) {
        this.unassignedPartitions = unassignedPartitions;
    }

    public MetricStatus getDistributionStatus() { return distributionStatus; }
    public void setDistributionStatus(MetricStatus distributionStatus) {
        this.distributionStatus = distributionStatus;
    }

    public MetricStatus getSafetyStatus() { return safetyStatus; }
    public void setSafetyStatus(MetricStatus safetyStatus) { this.safetyStatus = safetyStatus; }

    public MetricStatus getOverallStatus() { return overallStatus;}
    public void setOverallStatus(MetricStatus overallStatus) { this.overallStatus = overallStatus;}

    public MetricStatus getPartitionCountStatus() { return partitionCountStatus;}
    public void setPartitionCountStatus(MetricStatus partitionCountStatus) { this.partitionCountStatus = partitionCountStatus;}

    public MetricStatus getBalancedStatus() { return balancedStatus;}
    public void setBalancedStatus(MetricStatus balancedStatus) { this.balancedStatus = balancedStatus;}

    public MetricMeta getMeta() {return meta;}
    public void setMeta(MetricMeta meta) {this.meta = meta;}

    public static class MemberInfo {
        private String memberId;
        private String host;
        private int port;
        private boolean isLeader;

        public MemberInfo() {}

        public MemberInfo(String memberId, String host, int port, boolean isLeader) {
            this.memberId = memberId;
            this.host = host;
            this.port = port;
            this.isLeader = isLeader;
        }

        public String getMemberId() { return memberId; }
        public void setMemberId(String memberId) { this.memberId = memberId; }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }

        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }

        public boolean isLeader() { return isLeader; }
        public void setLeader(boolean leader) { isLeader = leader; }
    }

}
 