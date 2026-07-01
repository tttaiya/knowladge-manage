from __future__ import annotations

import logging
import time
from typing import List, Optional

from app.schemas.retrieval import (
    RetrievalCandidate,
    RetrievalSearchRequest,
    RetrievalSearchResponse,
)
from app.services.embeddings import EmbeddingClient
from app.services.rerank import RerankClient
from app.services.vector_store import (
    ChromaStore,
    VectorSearchHit,
)


logger = logging.getLogger(__name__)


class RetrievalService:
    """
    F5 向量检索与重排序业务服务。

    业务状态和标签过滤由 Java + MySQL 完成。
    FastAPI 只允许在 Java 提供的 docIds 范围内查询 ChromaDB。

    VECTOR_RERANK 模式下：
    - Rerank 正常：返回真实 rerankScore；
    - Rerank 异常：降级为 VECTOR_ONLY；
    - 降级时不伪造 rerankScore。
    """

    def __init__(
        self,
        *,
        embedding_client: EmbeddingClient,
        vector_store: ChromaStore,
        rerank_client: Optional[RerankClient] = None,
    ) -> None:
        self.embedding_client = embedding_client
        self.vector_store = vector_store
        self.rerank_client = rerank_client

    def search(
        self,
        request: RetrievalSearchRequest,
    ) -> RetrievalSearchResponse:
        start_time = time.perf_counter()

        # Java Search 必须先根据 READY、未删除和标签筛出 docIds。
        # docIds 为空时不能退化为全库搜索，否则会绕过业务门禁。
        if not request.doc_ids:
            return RetrievalSearchResponse(
                candidates=[],
                elapsedMs=self._elapsed_ms(start_time),
                rerankApplied=False,
            )

        query_embedding = (
            self.embedding_client.embed_query(
                request.query
            )
        )

        candidate_k = max(
            request.top_k,
            request.candidate_k,
        )

        hits = self.vector_store.search(
            query_embedding=query_embedding,
            top_k=candidate_k,
            doc_ids=request.doc_ids,
            kb_ids=(
                request.knowledge_base_ids
                if request.knowledge_base_ids
                else None
            ),
        )

        filtered_hits = [
            hit
            for hit in hits
            if (
                hit.similarity_score
                >= request.similarity_threshold
            )
        ]

        if request.mode == "VECTOR_RERANK":
            return self._search_with_rerank(
                request=request,
                filtered_hits=filtered_hits,
                start_time=start_time,
            )

        candidates = self._build_vector_candidates(
            hits=filtered_hits,
            top_k=request.top_k,
        )

        return RetrievalSearchResponse(
            candidates=candidates,
            elapsedMs=self._elapsed_ms(start_time),
            rerankApplied=False,
        )

    def _search_with_rerank(
        self,
        *,
        request: RetrievalSearchRequest,
        filtered_hits: List[VectorSearchHit],
        start_time: float,
    ) -> RetrievalSearchResponse:
        """
        执行真实重排序。

        Rerank 客户端未配置或调用失败时，
        按公共协议降级为 VECTOR_ONLY。
        """

        if not filtered_hits:
            return RetrievalSearchResponse(
                candidates=[],
                elapsedMs=self._elapsed_ms(start_time),
                rerankApplied=(
                    self.rerank_client is not None
                ),
            )

        if self.rerank_client is None:
            return self._build_degraded_response(
                filtered_hits=filtered_hits,
                top_k=request.top_k,
                start_time=start_time,
            )

        documents = [
            hit.document
            for hit in filtered_hits
        ]

        rerank_top_n = min(
            request.rerank_top_n,
            len(documents),
        )

        try:
            rerank_results = self.rerank_client.rerank(
                query=request.query,
                documents=documents,
                top_n=rerank_top_n,
            )
        except Exception as exc:
            logger.warning(
                "Rerank unavailable, degrade to VECTOR_ONLY: %s",
                exc,
            )

            return self._build_degraded_response(
                filtered_hits=filtered_hits,
                top_k=request.top_k,
                start_time=start_time,
            )

        candidates: List[RetrievalCandidate] = []

        for rerank_result in rerank_results:
            if (
                rerank_result.score
                < request.rerank_threshold
            ):
                continue

            hit = filtered_hits[
                rerank_result.index
            ]

            candidates.append(
                RetrievalCandidate(
                    vectorId=hit.vector_id,
                    distance=hit.distance,
                    similarityScore=(
                        hit.similarity_score
                    ),
                    rerankScore=(
                        rerank_result.score
                    ),
                )
            )

            if len(candidates) >= request.top_k:
                break

        return RetrievalSearchResponse(
            candidates=candidates,
            elapsedMs=self._elapsed_ms(start_time),
            rerankApplied=True,
        )

    def _build_degraded_response(
        self,
        *,
        filtered_hits: List[VectorSearchHit],
        top_k: int,
        start_time: float,
    ) -> RetrievalSearchResponse:
        """
        Rerank 不可用时降级为纯向量检索。

        不填写 rerankScore，防止伪造重排序结果。
        """

        candidates = self._build_vector_candidates(
            hits=filtered_hits,
            top_k=top_k,
        )

        return RetrievalSearchResponse(
            candidates=candidates,
            elapsedMs=self._elapsed_ms(start_time),
            rerankApplied=False,
            degradedMode="VECTOR_ONLY",
        )

    @staticmethod
    def _build_vector_candidates(
        *,
        hits: List[VectorSearchHit],
        top_k: int,
    ) -> List[RetrievalCandidate]:
        candidates: List[RetrievalCandidate] = []

        for hit in hits:
            candidates.append(
                RetrievalCandidate(
                    vectorId=hit.vector_id,
                    distance=hit.distance,
                    similarityScore=(
                        hit.similarity_score
                    ),
                )
            )

            if len(candidates) >= top_k:
                break

        return candidates

    @staticmethod
    def _elapsed_ms(start_time: float) -> int:
        return max(
            0,
            int(
                (
                    time.perf_counter()
                    - start_time
                )
                * 1000
            ),
        )