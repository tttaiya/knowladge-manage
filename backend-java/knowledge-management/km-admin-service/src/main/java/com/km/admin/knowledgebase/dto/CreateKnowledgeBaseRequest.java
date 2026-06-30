package com.km.admin.knowledgebase.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 创建知识库请求。
 * F2 v1.0 5.1 节字段。
 *
 * <p>设计要点：
 * <ul>
 *   <li>name 必填，1~128 字符，trim 后非空
 *   <li>category 取值由 service 层校验
 *   <li>retrievalStrategy 必填，取值 VECTOR_RERANK / SEMANTIC
 *   <li>chunkStrategy 选填，默认 HEADING；FIXED 时 chunkOverlap < chunkSize
 *   <li>separatorsJson 为 JSON 字符串（List&lt;String&gt; 序列化），由 service 层 Jackson 解析
 * </ul>
 */
public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 128, message = "知识库名称长度不能超过 128 字符")
    private String name;

    @Size(max = 500, message = "描述长度不能超过 500 字符")
    private String description;

    @NotBlank(message = "分类不能为空")
    private String category;

    @NotBlank(message = "检索策略不能为空")
    @Pattern(regexp = "VECTOR_RERANK|SEMANTIC", message = "retrievalStrategy 必须是 VECTOR_RERANK 或 SEMANTIC")
    private String retrievalStrategy;

    @Pattern(regexp = "HEADING|FIXED", message = "chunkStrategy 必须是 HEADING 或 FIXED")
    private String chunkStrategy;

    @Min(value = 50, message = "chunkSize 不能小于 50")
    private Integer chunkSize;

    @Min(value = 0, message = "chunkOverlap 不能小于 0")
    private Integer chunkOverlap;

    /** JSON 字符串，例如 ["\n\n","\n","。"] */
    private String separatorsJson;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRetrievalStrategy() { return retrievalStrategy; }
    public void setRetrievalStrategy(String retrievalStrategy) { this.retrievalStrategy = retrievalStrategy; }

    public String getChunkStrategy() { return chunkStrategy; }
    public void setChunkStrategy(String chunkStrategy) { this.chunkStrategy = chunkStrategy; }

    public Integer getChunkSize() { return chunkSize; }
    public void setChunkSize(Integer chunkSize) { this.chunkSize = chunkSize; }

    public Integer getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(Integer chunkOverlap) { this.chunkOverlap = chunkOverlap; }

    public String getSeparatorsJson() { return separatorsJson; }
    public void setSeparatorsJson(String separatorsJson) { this.separatorsJson = separatorsJson; }
}
