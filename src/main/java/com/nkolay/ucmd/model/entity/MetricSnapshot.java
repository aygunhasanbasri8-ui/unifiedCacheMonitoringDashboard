package com.nkolay.ucmd.model.entity;

import java.time.LocalDateTime;

import com.nkolay.ucmd.model.dto.MetricStatus;
import jakarta.persistence.*;

@Entity
@Table(name = "metric_snapshots")
public class MetricSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)


    private Long id;
    private Long instanceId;
    private LocalDateTime collectedAt;
    private String status;           // UP | DOWN
    private String version;
    private long uptimeSeconds;
    private double cpuPercent;
    private long memoryUsed;
    private long memoryMax;
    private long keyCount;
    private int clientCount;
    private long connectionErrors;
    private double fragmentationRatio;
    private int nodeCount;
    private String partitionJson;   // Hazelcast partition dağılımı

    @Enumerated(EnumType.STRING)
    private com.nkolay.ucmd.model.dto.MetricStatus overallStatus;

    public MetricSnapshot() {
        this.collectedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }

    public LocalDateTime getCollectedAt() { return collectedAt; }
    public void setCollectedAt(LocalDateTime collectedAt) { this.collectedAt = collectedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }

    public double getCpuPercent() { return cpuPercent; }
    public void setCpuPercent(double cpuPercent) { this.cpuPercent = cpuPercent; }

    public long getMemoryUsed() { return memoryUsed; }
    public void setMemoryUsed(long memoryUsed) { this.memoryUsed = memoryUsed; }

    public long getMemoryMax() { return memoryMax; }
    public void setMemoryMax(long memoryMax) { this.memoryMax = memoryMax; }

    public long getKeyCount() { return keyCount; }
    public void setKeyCount(long keyCount) { this.keyCount = keyCount; }

    public int getClientCount() { return clientCount; }
    public void setClientCount(int clientCount) { this.clientCount = clientCount; }

    public long getConnectionErrors() { return connectionErrors; }
    public void setConnectionErrors(long connectionErrors) { this.connectionErrors = connectionErrors; }

    public double getFragmentationRatio() { return fragmentationRatio; }
    public void setFragmentationRatio(double fragmentationRatio) { this.fragmentationRatio = fragmentationRatio; }

    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }

    public String getPartitionJson() { return partitionJson; }
    public void setPartitionJson(String partitionJson) { this.partitionJson = partitionJson; }

    public com.nkolay.ucmd.model.dto.MetricStatus getOverallStatus() { return overallStatus;}
    public void setOverallStatus (com.nkolay.ucmd.model.dto.MetricStatus overallStatus) {this.overallStatus = overallStatus;}
}