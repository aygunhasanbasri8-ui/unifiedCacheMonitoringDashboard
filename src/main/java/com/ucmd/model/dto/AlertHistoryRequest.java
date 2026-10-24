package com.ucmd.model.dto;

/** Alarm geçmişi filtresi; ikisi de isteğe bağlı. */
public class AlertHistoryRequest {

    private Long instanceId;
    private Integer limit;

    public Long getInstanceId() { return instanceId; }
    public void setInstanceId(Long instanceId) { this.instanceId = instanceId; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
