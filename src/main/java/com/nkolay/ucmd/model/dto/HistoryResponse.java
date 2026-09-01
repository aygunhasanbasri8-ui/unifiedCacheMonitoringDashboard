package com.nkolay.ucmd.model.dto;

import com.nkolay.ucmd.enums.MetricStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class HistoryResponse {

    private List<HistoryPoint> points = new ArrayList<>();
    private int pointCount;
    private Instant from;
    private Instant to;
    private String resolution;       // RAW | DOWNSAMPLED
    private MetricStatus status;
    private MetricMeta meta = new MetricMeta();

    public HistoryResponse() {}

    public List<HistoryPoint> getPoints() { return points; }
    public void setPoints(List<HistoryPoint> points) { this.points = points; }

    public int getPointCount() { return pointCount; }
    public void setPointCount(int pointCount) { this.pointCount = pointCount; }

    public Instant getFrom() { return from; }
    public void setFrom(Instant from) { this.from = from; }

    public Instant getTo() { return to; }
    public void setTo(Instant to) { this.to = to; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public MetricStatus getStatus() { return status; }
    public void setStatus(MetricStatus status) { this.status = status; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }
}
