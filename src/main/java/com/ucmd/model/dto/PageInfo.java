package com.ucmd.model.dto;

import com.ucmd.enums.MetricStatus;

import java.util.ArrayList;
import java.util.List;

public class PageInfo<T> {
    private List<T> content = new ArrayList<>();
    private String cursor;
    private boolean hasMore;
    private int pageSize;
    private long totalElements;

    private MetricStatus status = MetricStatus.AVAILABLE;
    private MetricMeta meta = new MetricMeta();

    public PageInfo() {}

    public PageInfo(List<T> content, String cursor, boolean hasMore, int pageSize) {
        this(content, cursor, hasMore, pageSize, MetricStatus.AVAILABLE);
    }

    public PageInfo(List<T> content, String cursor, boolean hasMore, int pageSize,
                    MetricStatus status) {
        this.content = content != null ? content : new ArrayList<>();
        this.cursor = cursor;
        this.hasMore = hasMore;
        this.pageSize = pageSize;
        this.totalElements = this.content.size();
        this.status = status;
    }

    public static <T> PageInfo<T> unavailable(int pageSize) {
        return new PageInfo<>(new ArrayList<>(), "0", false, pageSize,
                MetricStatus.UNAVAILABLE);
    }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public String getCursor() { return cursor; }
    public void setCursor(String cursor) { this.cursor = cursor; }

    public boolean isHasMore() { return hasMore; }
    public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }

    public MetricStatus getStatus() { return status; }
    public void setStatus(MetricStatus status) { this.status = status; }

    public MetricMeta getMeta() { return meta; }
    public void setMeta(MetricMeta meta) { this.meta = meta; }
}
