package com.nkolay.ucmd.model.dto;

public class SnapshotQueryRequest {
    private Integer lastMinutes;
    private Integer maxPoints;       // varsayılan 500, tavan 2000

    public SnapshotQueryRequest() {}

    public Integer getLastMinutes() { return lastMinutes;}
    public void setLastMinutes(Integer lastMinutes) {this.lastMinutes = lastMinutes;}

    public Integer getMaxPoints() { return maxPoints; }
    public void setMaxPoints(Integer maxPoints) { this.maxPoints = maxPoints; }
}
