package com.km.admin.knowledgebase.dto;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 批量删除知识库请求。
 * F2 v1.0 5.4 节批量删除。
 *
 * <p>约束：1~50 个 ID；任一不存在或任一存在未完成任务 → 2005 / 整批失败（5001）。
 */
public class BatchDeleteKnowledgeBaseRequest {

    @NotNull(message = "knowledgeBaseIds 不能为空")
    @NotEmpty(message = "至少选择一个知识库")
    @Size(max = 50, message = "单次最多删除 50 个知识库")
    private List<Long> knowledgeBaseIds;

    public List<Long> getKnowledgeBaseIds() { return knowledgeBaseIds; }
    public void setKnowledgeBaseIds(List<Long> knowledgeBaseIds) { this.knowledgeBaseIds = knowledgeBaseIds; }
}
