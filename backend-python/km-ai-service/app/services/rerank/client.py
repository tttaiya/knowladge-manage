from __future__ import annotations

import os
from dataclasses import dataclass
from typing import List, Optional, Sequence

import httpx


DEFAULT_API_BASE = (
    "https://dashscope.aliyuncs.com/compatible-api/v1"
)
DEFAULT_MODEL = "qwen3-rerank"
DEFAULT_TIMEOUT_SECONDS = 30.0
DEFAULT_INSTRUCTION = (
    "Given a web search query, retrieve relevant passages "
    "that answer the query."
)


class RerankError(RuntimeError):
    """Rerank 调用失败的基础异常。"""


class RerankConfigError(RerankError):
    """Rerank 配置缺失或不合法。"""


class RerankResponseError(RerankError):
    """Rerank 服务返回的数据结构不合法。"""


@dataclass(frozen=True)
class RerankResult:
    """一条重排序结果。"""

    index: int
    score: float


class RerankClient:
    """阿里云百炼 qwen3-rerank HTTP 客户端。"""

    def __init__(
        self,
        *,
        api_base: Optional[str] = None,
        api_key: Optional[str] = None,
        model: Optional[str] = None,
        instruction: Optional[str] = None,
        timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
        http_client: Optional[httpx.Client] = None,
    ) -> None:
        self.api_base = (
            api_base
            or os.getenv("RERANK_API_BASE")
            or DEFAULT_API_BASE
        ).rstrip("/")

        self.api_key = (
            api_key
            if api_key is not None
            else os.getenv("DASHSCOPE_API_KEY", "")
        ).strip()

        self.model = (
            model
            or os.getenv("RERANK_MODEL")
            or DEFAULT_MODEL
        ).strip()

        self.instruction = (
            instruction
            or os.getenv("RERANK_INSTRUCTION")
            or DEFAULT_INSTRUCTION
        ).strip()

        if timeout_seconds <= 0:
            raise ValueError(
                "timeout_seconds 必须大于 0"
            )

        self.timeout_seconds = timeout_seconds
        self._owns_http_client = http_client is None
        self._http_client = http_client or httpx.Client(
            timeout=timeout_seconds,
        )

    @property
    def endpoint(self) -> str:
        return f"{self.api_base}/reranks"

    def close(self) -> None:
        if self._owns_http_client:
            self._http_client.close()

    def __enter__(self) -> "RerankClient":
        return self

    def __exit__(
        self,
        exc_type,
        exc_value,
        traceback,
    ) -> None:
        self.close()

    def rerank(
        self,
        *,
        query: str,
        documents: Sequence[str],
        top_n: int,
    ) -> List[RerankResult]:
        """
        根据 query 对候选文档进行相关性重排序。

        返回结果中的 index 对应输入 documents 的下标。
        """

        normalized_query = str(query).strip()

        if not normalized_query:
            raise ValueError("Rerank query 不能为空")

        normalized_documents = [
            str(document).strip()
            for document in documents
        ]

        if not normalized_documents:
            return []

        if any(
            not document
            for document in normalized_documents
        ):
            raise ValueError(
                "Rerank documents 不能包含空文本"
            )

        if top_n < 1:
            raise ValueError(
                "Rerank top_n 必须大于等于 1"
            )

        if not self.api_key:
            raise RerankConfigError(
                "未配置 DASHSCOPE_API_KEY"
            )

        actual_top_n = min(
            top_n,
            len(normalized_documents),
        )

        payload = {
            "model": self.model,
            "query": normalized_query,
            "documents": normalized_documents,
            "top_n": actual_top_n,
            "instruct": self.instruction,
        }

        headers = {
            "Authorization": (
                f"Bearer {self.api_key}"
            ),
            "Content-Type": "application/json",
        }

        try:
            response = self._http_client.post(
                self.endpoint,
                headers=headers,
                json=payload,
            )
        except httpx.TimeoutException as exc:
            raise RerankError(
                "Rerank 请求超时："
                f"{self.timeout_seconds} 秒"
            ) from exc
        except httpx.HTTPError as exc:
            raise RerankError(
                f"Rerank 网络请求失败：{exc}"
            ) from exc

        if response.status_code >= 400:
            response_text = response.text[:500]

            raise RerankError(
                "Rerank 服务调用失败，"
                f"HTTP {response.status_code}："
                f"{response_text}"
            )

        try:
            body = response.json()
        except ValueError as exc:
            raise RerankResponseError(
                "Rerank 服务返回的不是有效 JSON"
            ) from exc

        results = body.get("results")

        if not isinstance(results, list):
            raise RerankResponseError(
                "Rerank 响应缺少 results 数组"
            )

        parsed_results: List[RerankResult] = []
        used_indexes = set()

        for item in results:
            if not isinstance(item, dict):
                raise RerankResponseError(
                    "Rerank results 中存在非对象元素"
                )

            try:
                index = int(item["index"])
                score = float(
                    item["relevance_score"]
                )
            except (
                KeyError,
                TypeError,
                ValueError,
            ) as exc:
                raise RerankResponseError(
                    "Rerank 结果缺少合法的 "
                    "index 或 relevance_score"
                ) from exc

            if not (
                0
                <= index
                < len(normalized_documents)
            ):
                raise RerankResponseError(
                    "Rerank 返回的 index 超出"
                    "候选文档范围"
                )

            if index in used_indexes:
                raise RerankResponseError(
                    "Rerank 返回了重复的 index"
                )

            if not 0.0 <= score <= 1.0:
                raise RerankResponseError(
                    "Rerank relevance_score "
                    "必须位于 0 到 1 之间"
                )

            used_indexes.add(index)

            parsed_results.append(
                RerankResult(
                    index=index,
                    score=score,
                )
            )

        return parsed_results