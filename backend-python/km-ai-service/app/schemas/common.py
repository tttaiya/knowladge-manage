from __future__ import annotations

import json
import os
from typing import Any, List, Optional
from pydantic import BaseModel, ConfigDict, Field, field_validator


class CamelModel(BaseModel):
    """Pydantic 基类：允许 Java 传 camelCase，也允许 Python 内部用 snake_case。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")


class TaskPayload(CamelModel):
    """Worker 放在 taskPayloadJson 里的解析/切片配置。"""

    parse_backend: str = Field("pymupdf", alias="parseBackend")
    chunk_mode: str = Field("fixed", alias="chunkMode")  # fixed / heading
    chunk_size: int = Field(500, alias="chunkSize")
    overlap: int = 50
    separator: Any = "\n\n"
    enable_ocr: bool = Field(True, alias="enableOcr")
    min_pdf_text_chars: int = Field(30, alias="minPdfTextChars")
    max_pdf_pages: int = Field(
        default_factory=lambda: int(os.environ.get("MAX_PDF_PAGES", "200")),
        alias="maxPdfPages",
    )

    @field_validator("chunk_mode")
    @classmethod
    def validate_chunk_mode(cls, value: str) -> str:
        value = (value or "fixed").lower()
        if value not in {"fixed", "heading"}:
            raise ValueError("chunkMode 只支持 fixed 或 heading")
        return value

    @field_validator("chunk_size")
    @classmethod
    def validate_chunk_size(cls, value: int) -> int:
        if value < 50:
            return 50
        if value > 5000:
            return 5000
        return value

    @field_validator("overlap")
    @classmethod
    def validate_overlap(cls, value: int) -> int:
        if value < 0:
            return 0
        if value > 1000:
            return 1000
        return value

    @field_validator("max_pdf_pages")
    @classmethod
    def validate_max_pdf_pages(cls, value: int) -> int:
        if value < 1:
            raise ValueError("maxPdfPages 必须大于 0")
        return min(value, 2000)

    def separators(self) -> List[str]:
        """把 separator 统一转成分隔符列表。"""
        default = ["\n\n", "\n", "。", "；", ";", "，", ",", " "]
        if self.separator is None:
            return default
        if isinstance(self.separator, list):
            result = [str(item) for item in self.separator if str(item)]
            return result or default
        sep = str(self.separator)
        # 组长给的是 separator: "\n\n"，这里补上常用分隔符，避免只按一种分隔符切不动。
        result = [sep, "\n", "。", "；", ";", "，", ",", " "]
        # 去重但保留顺序
        seen = set()
        final = []
        for item in result:
            if item not in seen and item:
                final.append(item)
                seen.add(item)
        return final


def parse_task_payload(value: Any) -> TaskPayload:
    """兼容 Java 传 dict 或 JSON 字符串。"""
    if value is None:
        return TaskPayload()
    if isinstance(value, TaskPayload):
        return value
    if isinstance(value, str):
        try:
            return parse_task_payload(json.loads(value))
        except json.JSONDecodeError:
            raise ValueError("taskPayloadJson 不是合法 JSON 字符串")
    if isinstance(value, dict):
        data = dict(value)
        snapshot = data.get("knowledgeBaseSnapshot")
        source = dict(snapshot) if isinstance(snapshot, dict) else {}
        source.update(data)

        # 兼容 F2 已落地的知识库快照字段，同时保留 F4 顶层字段优先级。
        if "chunkMode" not in data and source.get("chunkStrategy") is not None:
            data["chunkMode"] = source["chunkStrategy"]
        if "overlap" not in data and source.get("chunkOverlap") is not None:
            data["overlap"] = source["chunkOverlap"]
        if "separator" not in data and source.get("separators") is not None:
            data["separator"] = source["separators"]
        if "chunkSize" not in data and source.get("chunkSize") is not None:
            data["chunkSize"] = source["chunkSize"]

        return TaskPayload.model_validate(data)
    raise ValueError("taskPayloadJson 只支持对象或 JSON 字符串")
