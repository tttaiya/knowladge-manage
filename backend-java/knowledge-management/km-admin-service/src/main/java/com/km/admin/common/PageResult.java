package com.km.admin.common;

import java.util.Collections;
import java.util.List;

/**
 * 分页响应包装。Java 8 兼容：使用 Collections.emptyList() 而非 List.of()。
 */
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int pageSize;

    public PageResult() {}

    public PageResult(List<T> records, long total, int page, int pageSize) {
        this.records = records == null ? Collections.<T>emptyList() : records;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int pageSize) {
        return new PageResult<>(records, total, page, pageSize);
    }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
