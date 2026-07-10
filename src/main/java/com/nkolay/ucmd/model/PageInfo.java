package com.nkolay.ucmd.model;

import java.util.ArrayList;
import java.util.List;

public class PageInfo<T> {
    private List<T> content = new ArrayList<>();
    private String cursor;
    private boolean hasMore;
    private int pageSize;
    private long totalElements;

    public PageInfo() {}

    public PageInfo(List<T> content, String cursor, boolean hasMore, int pageSize) {
        this.content = content;
        this.cursor = cursor;
        this.hasMore = hasMore;
        this.pageSize = pageSize;
        this.totalElements = content.size();
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
}
