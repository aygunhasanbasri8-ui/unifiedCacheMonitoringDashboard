package com.ucmd.model.dto;

import com.ucmd.enums.MetricStatus;

import java.util.ArrayList;
import java.util.List;


public class OperationStatsInfo implements MonitoredMetric {

    // operation
    private Long queueSize;
    private Long priorityQueueSize;
    private Long runningCount;
    private Long completedCount;
    private Long responseQueueSize;
    private Long genericQueueSize;
    private Integer partitionThreadCount;
    private Integer genericThreadCount;

    // operation.invocations
    private Long pendingInvocations;
    private Long backupTimeouts;
    private Long normalTimeouts;

    // operation.responses
    private Long responseErrorCount;
    private Long responseTimeoutCount;
    private Long responseMissingCount;

    // event
    private Long eventQueueSize;
    private Long eventsProcessed;
    private Long eventRejectedCount;
    private Long syncDeliveryFailureCount;

    // executor.internal (her executor ayrı)
    private List<ExecutorDetail> executors = new ArrayList<>();

    private MetricStatus overallStatus;
    private MetricStatus operationStatus;
    private MetricStatus eventStatus;
    private MetricStatus executorStatus;
    private MetricMeta meta = new MetricMeta();

    public OperationStatsInfo() {}

    public static OperationStatsInfo unavailable() {
        OperationStatsInfo o = new OperationStatsInfo();
        o.setOverallStatus(MetricStatus.UNAVAILABLE);
        return o;
    }

    public static OperationStatsInfo notApplicable() {
        OperationStatsInfo o = new OperationStatsInfo();
        o.setOverallStatus(MetricStatus.NOT_APPLICABLE);
        return o;
    }

    public Long getQueueSize() { return queueSize; }
    public void setQueueSize(Long v) { this.queueSize = v; }

    public Long getPriorityQueueSize() { return priorityQueueSize; }
    public void setPriorityQueueSize(Long v) { this.priorityQueueSize = v; }

    public Long getRunningCount() { return runningCount; }
    public void setRunningCount(Long v) { this.runningCount = v; }

    public Long getCompletedCount() { return completedCount; }
    public void setCompletedCount(Long v) { this.completedCount = v; }

    public Long getResponseQueueSize() { return responseQueueSize; }
    public void setResponseQueueSize(Long v) { this.responseQueueSize = v; }

    public Long getGenericQueueSize() { return genericQueueSize; }
    public void setGenericQueueSize(Long v) { this.genericQueueSize = v; }

    public Integer getPartitionThreadCount() { return partitionThreadCount; }
    public void setPartitionThreadCount(Integer v) { this.partitionThreadCount = v; }

    public Integer getGenericThreadCount() { return genericThreadCount; }
    public void setGenericThreadCount(Integer v) { this.genericThreadCount = v; }

    public Long getPendingInvocations() { return pendingInvocations; }
    public void setPendingInvocations(Long v) { this.pendingInvocations = v; }

    public Long getBackupTimeouts() { return backupTimeouts; }
    public void setBackupTimeouts(Long v) { this.backupTimeouts = v; }

    public Long getNormalTimeouts() { return normalTimeouts; }
    public void setNormalTimeouts(Long v) { this.normalTimeouts = v; }

    public Long getResponseErrorCount() { return responseErrorCount; }
    public void setResponseErrorCount(Long v) { this.responseErrorCount = v; }

    public Long getResponseTimeoutCount() { return responseTimeoutCount; }
    public void setResponseTimeoutCount(Long v) { this.responseTimeoutCount = v; }

    public Long getResponseMissingCount() { return responseMissingCount; }
    public void setResponseMissingCount(Long v) { this.responseMissingCount = v; }

    public Long getEventQueueSize() { return eventQueueSize; }
    public void setEventQueueSize(Long v) { this.eventQueueSize = v; }

    public Long getEventsProcessed() { return eventsProcessed; }
    public void setEventsProcessed(Long v) { this.eventsProcessed = v; }

    public Long getEventRejectedCount() { return eventRejectedCount; }
    public void setEventRejectedCount(Long v) { this.eventRejectedCount = v; }

    public Long getSyncDeliveryFailureCount() { return syncDeliveryFailureCount; }
    public void setSyncDeliveryFailureCount(Long v) { this.syncDeliveryFailureCount = v; }

    public List<ExecutorDetail> getExecutors() { return executors; }
    public void setExecutors(List<ExecutorDetail> v) { this.executors = v; }

    public MetricStatus getOverallStatus() { return overallStatus; }
    public void setOverallStatus(MetricStatus v) { this.overallStatus = v; }

    public MetricStatus getOperationStatus() { return operationStatus; }
    public void setOperationStatus(MetricStatus v) { this.operationStatus = v; }

    public MetricStatus getEventStatus() { return eventStatus; }
    public void setEventStatus(MetricStatus v) { this.eventStatus = v; }

    public MetricStatus getExecutorStatus() { return executorStatus; }
    public void setExecutorStatus(MetricStatus v) { this.executorStatus = v; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }

    public static class ExecutorDetail {
        private String name;
        private Long queueSize;
        private Long poolSize;
        private Long completedTaskCount;
        private Long remainingQueueCapacity;

        public ExecutorDetail() {}

        public ExecutorDetail(String name) { this.name = name; }

        public String getName() { return name; }
        public void setName(String v) { this.name = v; }

        public Long getQueueSize() { return queueSize; }
        public void setQueueSize(Long v) { this.queueSize = v; }

        public Long getPoolSize() { return poolSize; }
        public void setPoolSize(Long v) { this.poolSize = v; }

        public Long getCompletedTaskCount() { return completedTaskCount; }
        public void setCompletedTaskCount(Long v) { this.completedTaskCount = v; }

        public Long getRemainingQueueCapacity() { return remainingQueueCapacity; }
        public void setRemainingQueueCapacity(Long v) { this.remainingQueueCapacity = v; }
    }
}
