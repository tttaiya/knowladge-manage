"""知识管理检索服务客户端。"""

from typing import Any, Dict, List

import httpx
from loguru import logger

from app.config import config


class SearchResult:
    """搜索结果类"""

    def __init__(
        self,
        id: str,
        content: str,
        score: float,
        metadata: Dict[str, Any],
    ):
        self.id = id
        self.content = content
        self.score = score
        self.metadata = metadata

    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            "id": self.id,
            "content": self.content,
            "score": self.score,
            "metadata": self.metadata,
        }


class VectorSearchService:
    """通过知识管理模块检索文档切片。"""

    def __init__(self):
        """初始化检索服务客户端"""
        logger.info(f"知识管理检索客户端初始化完成: {config.retrieval_search_url}")

    def search_similar_documents(
        self,
        query: str,
        top_k: int = 3,
        knowledge_base_ids: list[str] | None = None,
    ) -> List[SearchResult]:
        """搜索相似文档。

        原 Milvus 检索已迁移到知识管理模块：
        POST /internal/v1/retrieval/search
        """
        try:
            logger.info(f"开始调用知识管理检索, 查询: {query}, topK: {top_k}")
            payload: Dict[str, Any] = {
                "query": query,
                "top_k": top_k,
            }
            if knowledge_base_ids:
                payload["knowledge_base_ids"] = knowledge_base_ids

            with httpx.Client(timeout=config.retrieval_request_timeout) as client:
                response = client.post(config.retrieval_search_url, json=payload)
                response.raise_for_status()
                body = response.json()

            rows = self._extract_rows(body)
            search_results = [self._to_search_result(row) for row in rows]
            logger.info(f"搜索完成, 找到 {len(search_results)} 个相似文档")
            return search_results

        except httpx.HTTPError as e:
            logger.error(f"调用知识管理检索失败: {e}")
            raise RuntimeError(f"搜索失败: {e}") from e
        except Exception as e:
            logger.error(f"解析知识管理检索结果失败: {e}")
            raise RuntimeError(f"搜索失败: {e}") from e

    def _extract_rows(self, body: Any) -> list[dict[str, Any]]:
        if isinstance(body, list):
            return [item for item in body if isinstance(item, dict)]
        if isinstance(body, dict):
            for key in ("results", "data", "items"):
                rows = body.get(key)
                if isinstance(rows, list):
                    return [item for item in rows if isinstance(item, dict)]
        return []

    def _to_search_result(self, row: dict[str, Any]) -> SearchResult:
        metadata = row.get("metadata") if isinstance(row.get("metadata"), dict) else {}
        chunk_id = row.get("chunk_id") or row.get("id") or metadata.get("chunk_id") or metadata.get("id")
        score = self._float(row.get("score"), self._float(row.get("normalized_score"), 1.0))
        normalized_score = self._float(row.get("normalized_score"), score)
        raw_score = self._float(row.get("raw_score"), score)
        merged_metadata = {
            **metadata,
            "id": row.get("id") or chunk_id,
            "chunk_id": chunk_id,
            "document_id": row.get("document_id") or metadata.get("document_id"),
            "knowledge_base_id": row.get("knowledge_base_id") or metadata.get("knowledge_base_id"),
            "knowledge_base_name": row.get("knowledge_base_name") or metadata.get("knowledge_base_name"),
            "file_name": row.get("document_name") or row.get("file_name") or metadata.get("file_name"),
            "section_path": row.get("section_path") or metadata.get("section_path"),
            "raw_score": raw_score,
            "normalized_score": normalized_score,
            "retrieval_source": "knowledge_management",
        }
        return SearchResult(
            id=str(chunk_id),
            content=str(row.get("content") or row.get("page_content") or ""),
            score=score,
            metadata=merged_metadata,
        )

    def _float(self, value: Any, default: float) -> float:
        try:
            return float(value)
        except (TypeError, ValueError):
            return default


# 全局单例
vector_search_service = VectorSearchService()
