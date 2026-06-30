package com.km.admin.knowledgebase.dto;

/**
 * 知识库分页查询请求。
 * F2 v1.0 5.4 节。
 *
 * <p>所有字段可选；nameKeyword 模糊匹配。
 */
public class QueryKnowledgeBaseRequest {

    private String category;
    private String nameKeyword;

    /** null=全部；0=活动；1=已删除 */
    private Integer isDeleted;

    private int pageNum = 1;
    private int pageSize = 10;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getNameKeyword() { return nameKeyword; }
    public void setNameKeyword(String nameKeyword) { this.nameKeyword = nameKeyword; }

    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }

    public int getPageNum() { return pageNum; }
    public void setPageNum(int pageNum) { this.pageNum = pageNum; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
}
