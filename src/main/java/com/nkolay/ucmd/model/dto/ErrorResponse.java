package com.nkolay.ucmd.model.dto;

public class ErrorResponse {
    private String type;           // Hata kategorisi
    private String title;          // Kısa açıklama
    private int status;            // HTTP status
    private String detail;         // Detaylı açıklama
    private String instance;       // Hangi resource'ta oldu
    private String traceId;        // Request trace ID
    private long timestamp;

    public ErrorResponse() {}

    public ErrorResponse(String type, String title, int status, String detail) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}