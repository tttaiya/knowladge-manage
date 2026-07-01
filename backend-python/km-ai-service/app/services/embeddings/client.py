from __future__ import annotations

import os
from typing import List, Optional, Sequence

import httpx


DEFAULT_API_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
DEFAULT_MODEL = "text-embedding-v4"
DEFAULT_DIMENSION = 1024
DEFAULT_BATCH_SIZE = 16
DEFAULT_TIMEOUT_SECONDS = 30.0


class EmbeddingError(RuntimeError):
    """Embedding 调用失败的基础异常。"""


class EmbeddingConfigError(EmbeddingError):
    """Embedding 配置不完整。"""


class EmbeddingResponseError(EmbeddingError):
    """Embedding 服务响应格式或向量内容不正确。"""


class EmbeddingClient:
    """阿里云百炼 OpenAI 兼容 Embedding 客户端。"""

    def __init__(
        self,
        *,
        api_base: Optional[str] = None,
        api_key: Optional[str] = None,
        model: Optional[str] = None,
        dimension: Optional[int] = None,
        batch_size: int = DEFAULT_BATCH_SIZE,
        timeout_seconds: float = DEFAULT_TIMEOUT_SECONDS,
        http_client: Optional[httpx.Client] = None,
    ) -> None:
        self.api_base = (
            api_base
            or os.getenv("EMBEDDING_API_BASE")
            or DEFAULT_API_BASE
        ).rstrip("/")

        self.api_key = (
            api_key
            if api_key is not None
            else os.getenv("DASHSCOPE_API_KEY", "")
        ).strip()

        self.model = (
            model
            or os.getenv("EMBEDDING_MODEL")
            or DEFAULT_MODEL
        ).strip()

        env_dimension = os.getenv("EMBEDDING_DIMENSION")
        self.dimension = (
            dimension
            if dimension is not None
            else int(env_dimension or DEFAULT_DIMENSION)
        )

        if batch_size < 1:
            raise ValueError("batch_size 必须大于等于 1")

        if self.dimension < 1:
            raise ValueError("dimension 必须大于等于 1")

        self.batch_size = batch_size
        self.timeout_seconds = timeout_seconds

        self._owns_http_client = http_client is None
        self._http_client = http_client or httpx.Client(
            timeout=timeout_seconds,
        )

    @property
    def endpoint(self) -> str:
        return f"{self.api_base}/embeddings"

    def close(self) -> None:
        if self._owns_http_client:
            self._http_client.close()

    def __enter__(self) -> "EmbeddingClient":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        self.close()

    def embed_query(self, query: str) -> List[float]:
        """将单个检索问题转换为向量。"""

        vectors = self.embed_texts([query])
        return vectors[0]

    def embed_texts(self, texts: Sequence[str]) -> List[List[float]]:
        """批量将切片文本转换为向量，并保持输入顺序。"""

        normalized_texts = [str(text).strip() for text in texts]

        if not normalized_texts:
            return []

        if any(not text for text in normalized_texts):
            raise ValueError("待向量化文本不能为空")

        if not self.api_key:
            raise EmbeddingConfigError(
                "未配置 DASHSCOPE_API_KEY"
            )

        vectors: List[List[float]] = []

        for start in range(0, len(normalized_texts), self.batch_size):
            batch = normalized_texts[start:start + self.batch_size]
            vectors.extend(self._embed_batch(batch))

        if len(vectors) != len(normalized_texts):
            raise EmbeddingResponseError(
                "Embedding 返回向量数量与输入文本数量不一致"
            )

        return vectors

    def _embed_batch(self, texts: Sequence[str]) -> List[List[float]]:
        payload = {
            "model": self.model,
            "input": list(texts),
            "dimensions": self.dimension,
            "encoding_format": "float",
        }

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        try:
            response = self._http_client.post(
                self.endpoint,
                headers=headers,
                json=payload,
            )
        except httpx.TimeoutException as exc:
            raise EmbeddingError(
                f"Embedding 请求超时：{self.timeout_seconds} 秒"
            ) from exc
        except httpx.HTTPError as exc:
            raise EmbeddingError(
                f"Embedding 网络请求失败：{exc}"
            ) from exc

        if response.status_code >= 400:
            response_text = response.text[:500]
            raise EmbeddingError(
                "Embedding 服务调用失败，"
                f"HTTP {response.status_code}：{response_text}"
            )

        try:
            body = response.json()
        except ValueError as exc:
            raise EmbeddingResponseError(
                "Embedding 服务返回的不是有效 JSON"
            ) from exc

        data = body.get("data")
        if not isinstance(data, list):
            raise EmbeddingResponseError(
                "Embedding 响应缺少 data 数组"
            )

        if len(data) != len(texts):
            raise EmbeddingResponseError(
                "Embedding 返回向量数量与当前批次文本数量不一致"
            )

        try:
            ordered_items = sorted(
                data,
                key=lambda item: int(item["index"]),
            )
        except (KeyError, TypeError, ValueError) as exc:
            raise EmbeddingResponseError(
                "Embedding 响应缺少合法的 index"
            ) from exc

        vectors: List[List[float]] = []

        for item in ordered_items:
            embedding = item.get("embedding")

            if not isinstance(embedding, list):
                raise EmbeddingResponseError(
                    "Embedding 响应中的 embedding 不是数组"
                )

            if len(embedding) != self.dimension:
                raise EmbeddingResponseError(
                    "Embedding 向量维度不正确："
                    f"期望 {self.dimension}，实际 {len(embedding)}"
                )

            try:
                vector = [float(value) for value in embedding]
            except (TypeError, ValueError) as exc:
                raise EmbeddingResponseError(
                    "Embedding 向量包含非数值内容"
                ) from exc

            vectors.append(vector)

        return vectors