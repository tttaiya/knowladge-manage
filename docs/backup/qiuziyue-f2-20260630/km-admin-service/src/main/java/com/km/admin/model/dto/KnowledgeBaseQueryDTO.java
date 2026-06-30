package com.km.admin.model.dto;

public class KnowledgeBaseQueryDTO {

    private String q;
    private String category;
    private Integer page = 1;
    private Integer pageSize = 10;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
