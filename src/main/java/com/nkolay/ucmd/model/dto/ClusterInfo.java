
package com.nkolay.ucmd.model.dto;

import java.util.ArrayList;
import java.util.List;

public class ClusterInfo {
    private int nodeCount;
    private int partitionCount;
    private List<MemberInfo> members = new ArrayList<>();
    private String clusterName;
    private boolean balanced;

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

    public boolean isBalanced() { return balanced; }
    public void setBalanced(boolean balanced) { this.balanced = balanced; }

    // Inner class for member info
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
 