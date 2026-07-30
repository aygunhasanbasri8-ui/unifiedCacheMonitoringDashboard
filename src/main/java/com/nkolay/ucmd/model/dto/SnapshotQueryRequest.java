package com.nkolay.ucmd.model.dto;

public class SnapshotQueryRequest {
    private Integer lastMinutes;
    public SnapshotQueryRequest() {}
    public Integer getLastMinutes() { return lastMinutes;}
    public void setLastMinutes(Integer lastMinutes) {this.lastMinutes = lastMinutes;}
}
