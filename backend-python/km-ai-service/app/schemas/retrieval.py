from __future__ import annotations

from typing import List, Literal, Optional

from pydantic import Field, field_validator

from .common import CamelModel


class EmbedTask(CamelModel):
    """Worker 传给 F5 向量化接口的任务基础信息。"""

    task_id: Optional[int] = Field(None, alias="taskId")
    doc_id: int = Field(..., alias="docId")
    kb_id: int = Field(..., alias="kbId")
    task_type: Optional[str] = Field(None, alias="taskType")
    trace_id: Optional[str] = Field(None, alias="traceId")
    target_version_no: int = Field(1, alias="targetVersionNo", ge=1)


class EmbedChunk(CamelModel):
    """
    待向量化切片。

    初次 EMBED 时，部分字段由 task 补齐；
    REEMBED 时应提供 chunkId、vectorId 和 contentVersion。
    """

    chunk_id: Optional[int] = Field(None, alias="chunkId")
    doc_id: Optional[int] = Field(None, alias="docId")
    kb_id: Optional[int] = Field(None, alias="kbId")
    version_no: Optional[int] = Field(None, alias="versionNo")

    chunk_index: int = Field(..., alias="chunkIndex", ge=1)
    content: str = Field(..., min_length=1)
    chapter_path: Optional[str] = Field(None, alias="chapterPath")
    page_no: Optional[int] = Field(None, alias="pageNo", ge=1)
    chunk_type: str = Field("paragraph", alias="chunkType")
    char_count: int = Field(0, alias="charCount", ge=0)

    vector_id: Optional[str] = Field(None, alias="vectorId")
    content_version: int = Field(1, alias="contentVersion", ge=1)

    @field_validator("content")
    @classmethod
    def validate_content(cls, value: str) -> str:
        content = value.strip()
        if not content:
            raise ValueError("content 不能为空")
        return content


class EmbedRequest(CamelModel):
    """真实向量化和重向量化的统一请求。"""

    operation: Literal["EMBED", "REEMBED"] = "EMBED"
    task: EmbedTask
    chunks: List[EmbedChunk] = Field(..., min_length=1)


class EmbeddedChunk(CamelModel):
    """向量化完成后返回给 Worker 的切片结果。"""

    chunk_id: Optional[int] = Field(None, alias="chunkId")
    doc_id: Optional[int] = Field(None, alias="docId")
    kb_id: Optional[int] = Field(None, alias="kbId")
    version_no: Optional[int] = Field(None, alias="versionNo")
    chunk_index: Optional[int] = Field(None, alias="chunkIndex")

    content: Optional[str] = None
    chapter_path: Optional[str] = Field(None, alias="chapterPath")
    page_no: Optional[int] = Field(None, alias="pageNo")
    chunk_type: Optional[str] = Field(None, alias="chunkType")
    char_count: Optional[int] = Field(None, alias="charCount")

    vector_id: str = Field(..., alias="vectorId")
    content_version: int = Field(1, alias="contentVersion", ge=1)


class EmbedResponse(CamelModel):
    """Worker 的 assertSuccess() 要求 success 必须为 true。"""

    success: bool
    doc_id: Optional[int] = Field(None, alias="docId")
    vector_ids: List[str] = Field(default_factory=list, alias="vectorIds")
    chunks: List[EmbeddedChunk] = Field(default_factory=list)

    error_stage: Optional[str] = Field(None, alias="errorStage")
    error_message: Optional[str] = Field(None, alias="errorMessage")


class RetrievalSearchRequest(CamelModel):
    """Java km-search-service 调用的内部向量检索请求。"""

    query: str = Field(..., min_length=1)
    knowledge_base_ids: List[int] = Field(
        default_factory=list,
        alias="knowledgeBaseIds",
    )
    doc_ids: List[int] = Field(default_factory=list, alias="docIds")

    # 标签由 Java + MySQL 过滤，字段仅保留兼容现有 DTO。
    tags: List[str] = Field(default_factory=list)

    mode: Literal["SEMANTIC", "VECTOR_ONLY", "VECTOR_RERANK"] = "SEMANTIC"
    top_k: int = Field(5, alias="topK", ge=1, le=100)
    candidate_k: int = Field(50, alias="candidateK", ge=1, le=1000)
    similarity_threshold: float = Field(
        0.0,
        alias="similarityThreshold",
        ge=0.0,
        le=1.0,
    )
    rerank_top_n: int = Field(10, alias="rerankTopN", ge=1, le=100)
    rerank_threshold: float = Field(
        0.0,
        alias="rerankThreshold",
        ge=0.0,
        le=1.0,
    )

    @field_validator("query")
    @classmethod
    def validate_query(cls, value: str) -> str:
        query = value.strip()
        if not query:
            raise ValueError("query 不能为空")
        return query


class RetrievalCandidate(CamelModel):
    """FastAPI 返回给 Java Search 的候选向量记录。"""

    vector_id: str = Field(..., alias="vectorId")

    # 仅为旧协议兼容保留；Java 后续以 vectorId 回查真实 chunkId。
    chunk_id: Optional[int] = Field(None, alias="chunkId")

    distance: Optional[float] = None
    similarity_score: Optional[float] = Field(None, alias="similarityScore")
    rerank_score: Optional[float] = Field(None, alias="rerankScore")


class RetrievalSearchResponse(CamelModel):
    """内部检索响应。"""

    candidates: List[RetrievalCandidate] = Field(default_factory=list)
    elapsed_ms: int = Field(0, alias="elapsedMs", ge=0)

    rerank_applied: bool = Field(False, alias="rerankApplied")
    degraded_mode: Optional[str] = Field(None, alias="degradedMode")


class DeleteVectorsResponse(CamelModel):
    """按文档或文档版本删除向量的响应。"""

    success: bool
    doc_id: int = Field(..., alias="docId")
    version_no: Optional[int] = Field(None, alias="versionNo")
    deleted_count: int = Field(0, alias="deletedCount", ge=0)

    error_stage: Optional[str] = Field(None, alias="errorStage")
    error_message: Optional[str] = Field(None, alias="errorMessage")