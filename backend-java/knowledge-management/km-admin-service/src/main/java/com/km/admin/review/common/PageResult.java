package com.km.admin.review.common;

import java.util.List;

/**
 * 分页响应包装。已去除 Lombok，改用显式 getter/setter。
 */
public class PageResult<T> {

    private Long total;
    private Integer page;
    private Integer pageSize;
    private List<T> records;

    public static <T> PageResult<T> of(Long total, Integer page, Integer pageSize, List<T> records) {
        PageResult<T> pageResult = new PageResult<T>();
        pageResult.setTotal(total);
        pageResult.setPage(page);
        pageResult.setPageSize(pageSize);
        pageResult.setRecords(records);
        return pageResult;
    }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }

    public List<T> getRecords() { return records; }
    public void setRecords(List<T> records) { this.records = records; }
}
