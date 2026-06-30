"""工具输出上下文压缩服务。"""

import json
import re
from typing import Any


class ContextCompressor:
    """将长日志和长工具结果压缩成结构化摘要。"""

    def __init__(self, max_inline_chars: int = 1200) -> None:
        self.max_inline_chars = max_inline_chars
        self.error_keywords = [
            "error",
            "exception",
            "failed",
            "timeout",
            "oom",
            "告警",
            "异常",
            "失败",
            "超时",
            "内存",
            "磁盘",
        ]

    def compress(self, value: Any) -> dict[str, Any]:
        text = self._to_text(value)
        if len(text) <= self.max_inline_chars:
            return {
                "compressed": False,
                "summary_text": text,
                "structured_summary": {
                    "affected_service": self._extract_services(text),
                    "time_range": self._extract_time_range(text),
                    "key_errors": self._extract_evidence(text)[:5],
                    "metric_abnormalities": self._extract_metric_abnormalities(text),
                    "possible_causes": self._infer_possible_causes(text),
                    "evidence_snippets": [text[:500]] if text else [],
                    "confidence": 0.7,
                },
            }

        snippets = self._extract_evidence(text)
        structured = {
            "affected_service": self._extract_services(text)[:5],
            "time_range": self._extract_time_range(text),
            "key_errors": snippets[:10],
            "metric_abnormalities": self._extract_metric_abnormalities(text)[:10],
            "possible_causes": self._infer_possible_causes(text),
            "evidence_snippets": snippets[:5],
            "confidence": 0.75 if snippets else 0.45,
        }
        return {
            "compressed": True,
            "summary_text": json.dumps(structured, ensure_ascii=False, indent=2),
            "structured_summary": structured,
        }

    def _to_text(self, value: Any) -> str:
        if isinstance(value, str):
            return value
        return json.dumps(value, ensure_ascii=False, default=str)

    def _extract_evidence(self, text: str) -> list[str]:
        lines = [line.strip() for line in text.splitlines() if line.strip()]
        matched = [
            line[:300]
            for line in lines
            if any(keyword in line.lower() for keyword in self.error_keywords)
        ]
        return matched or [line[:300] for line in lines[:5]]

    def _extract_metric_abnormalities(self, text: str) -> list[str]:
        patterns = [
            r"cpu[^,，\n]{0,40}\d{2,3}%",
            r"memory[^,，\n]{0,40}\d{2,3}%",
            r"disk[^,，\n]{0,40}\d{2,3}%",
            r"内存[^,，\n]{0,40}\d{2,3}%",
            r"磁盘[^,，\n]{0,40}\d{2,3}%",
        ]
        results: list[str] = []
        for pattern in patterns:
            for match in re.finditer(pattern, text, flags=re.IGNORECASE):
                results.append(match.group(0))
        return list(dict.fromkeys(results))

    def _extract_services(self, text: str) -> list[str]:
        pattern = r"\b[a-zA-Z][a-zA-Z0-9_-]{2,40}-(?:service|api|worker)\b"
        return list(dict.fromkeys(re.findall(pattern, text)))

    def _extract_time_range(self, text: str) -> str | None:
        match = re.search(r"\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?", text)
        return match.group(0) if match else None

    def _infer_possible_causes(self, text: str) -> list[str]:
        lower = text.lower()
        causes = []
        if "oom" in lower or "memory" in lower or "内存" in lower:
            causes.append("可能存在内存压力或 OOM")
        if "timeout" in lower or "超时" in lower:
            causes.append("可能存在下游依赖超时或接口响应慢")
        if "disk" in lower or "磁盘" in lower:
            causes.append("可能存在磁盘空间或 IO 问题")
        if "connection" in lower or "连接" in lower:
            causes.append("可能存在网络连接或依赖服务异常")
        return causes


context_compressor = ContextCompressor()
