from __future__ import annotations

from typing import List, Optional, Any
from pydantic import Field, field_validator

from .common import CamelModel, TaskPayload, parse_task_payload


class WorkerTaskRequest(CamelModel):
    """Worker 传给 FastAPI 的原始任务请求。字段名按组长给的格式。"""

    task_id: Optional[int] = Field(None, alias="taskId")
    doc_id: int = Field(..., alias="docId")
    kb_id: int = Field(..., alias="kbId")
    task_type: Optional[str] = Field(None, alias="taskType")
    trigger_source: Optional[str] = Field(None, alias="triggerSource")
    trace_id: Optional[str] = Field(None, alias="traceId")
    file_path: str = Field(..., alias="filePath")
    extension: Optional[str] = None
    strategy_version: Optional[int] = Field(None, alias="strategyVersion")
    target_version_no: Optional[int] = Field(None, alias="targetVersionNo")
    task_payload_json: TaskPayload = Field(default_factory=TaskPayload, alias="taskPayloadJson")

    @field_validator("task_payload_json", mode="before")
    @classmethod
    def normalize_payload(cls, value: Any) -> TaskPayload:
        return parse_task_payload(value)


class ParsedBlock(CamelModel):
    """解析后的结构化文本块。"""

    content: str
    page_no: Optional[int] = Field(None, alias="pageNo")
    block_type: str = Field("paragraph", alias="blockType")  # paragraph / ocr / table
    chapter_path: Optional[str] = Field(None, alias="chapterPath")
    char_count: int = Field(0, alias="charCount")

    @field_validator("char_count", mode="before")
    @classmethod
    def set_char_count(cls, value: Any, info):
        if value:
            return value
        data = info.data if hasattr(info, "data") else {}
        content = data.get("content", "")
        return len(content or "")


class ParseResponse(CamelModel):
    success: bool = True
    task_id: Optional[int] = Field(None, alias="taskId")
    doc_id: Optional[int] = Field(None, alias="docId")
    kb_id: Optional[int] = Field(None, alias="kbId")
    trace_id: Optional[str] = Field(None, alias="traceId")
    extension: Optional[str] = None
    parsed_text: str = Field("", alias="parsedText")
    blocks: List[ParsedBlock] = Field(default_factory=list)
    error_stage: Optional[str] = Field(None, alias="errorStage")
    error_message: Optional[str] = Field(None, alias="errorMessage")


class ChunkPayload(CamelModel):
    """返回给 Worker 的 chunk，字段对齐 km_document_chunk 表。"""

    content: str
    chapter_path: Optional[str] = Field(None, alias="chapterPath")
    page_no: Optional[int] = Field(None, alias="pageNo")
    chunk_type: str = Field("paragraph", alias="chunkType")
    char_count: int = Field(0, alias="charCount")
    chunk_index: int = Field(1, alias="chunkIndex")


class ChunkRequest(CamelModel):
    """Worker 调 chunk 阶段时传入。支持 blocks 或 parsedText 两种输入。"""

    task_id: Optional[int] = Field(None, alias="taskId")
    doc_id: int = Field(..., alias="docId")
    kb_id: int = Field(..., alias="kbId")
    trace_id: Optional[str] = Field(None, alias="traceId")
    parsed_text: Optional[str] = Field(None, alias="parsedText")
    blocks: List[ParsedBlock] = Field(default_factory=list)
    task_payload_json: TaskPayload = Field(default_factory=TaskPayload, alias="taskPayloadJson")

    @field_validator("task_payload_json", mode="before")
    @classmethod
    def normalize_payload(cls, value: Any) -> TaskPayload:
        return parse_task_payload(value)


class ChunkResponse(CamelModel):
    success: bool = True
    task_id: Optional[int] = Field(None, alias="taskId")
    doc_id: Optional[int] = Field(None, alias="docId")
    kb_id: Optional[int] = Field(None, alias="kbId")
    trace_id: Optional[str] = Field(None, alias="traceId")
    chunks: List[ChunkPayload] = Field(default_factory=list)
    chunk_count: int = Field(0, alias="chunkCount")
    error_stage: Optional[str] = Field(None, alias="errorStage")
    error_message: Optional[str] = Field(None, alias="errorMessage")


class ProcessDocumentResponse(CamelModel):
    """本地调试接口：parse + chunk 一次完成。"""

    success: bool = True
    task_id: Optional[int] = Field(None, alias="taskId")
    doc_id: Optional[int] = Field(None, alias="docId")
    kb_id: Optional[int] = Field(None, alias="kbId")
    trace_id: Optional[str] = Field(None, alias="traceId")
    extension: Optional[str] = None
    parsed_text: str = Field("", alias="parsedText")
    blocks: List[ParsedBlock] = Field(default_factory=list)
    chunks: List[ChunkPayload] = Field(default_factory=list)
    chunk_count: int = Field(0, alias="chunkCount")
    error_stage: Optional[str] = Field(None, alias="errorStage")
    error_message: Optional[str] = Field(None, alias="errorMessage")
