from __future__ import annotations

from typing import List

import pytest

from app.schemas.retrieval import (
    RetrievalSearchRequest,
)
from app.services.rerank import RerankResult
from app.services.retrieval_service import (
    RetrievalService,
)
from app.services.vector_store import (
    VectorSearchHit,
)


class FakeEmbeddingClient:
    def __init__(self) -> None:
        self.received_queries: List[str] = []

    def embed_query(self, query: str):
        self.received_queries.append(query)

        return [
            1.0,
            0.0,
            0.0,
        ]


class FakeVectorStore:
    def __init__(self, hits=None) -> None:
        self.hits = list(hits or [])
        self.calls = []

    def search(
        self,
        *,
        query_embedding,
        top_k,
        doc_ids=None,
        kb_ids=None,
    ):
        self.calls.append(
            {
                "query_embedding": query_embedding,
                "top_k": top_k,
                "doc_ids": doc_ids,
                "kb_ids": kb_ids,
            }
        )

        return self.hits


class FakeRerankClient:
    def __init__(
        self,
        *,
        results=None,
        error: Exception | None = None,
    ) -> None:
        self.results = list(results or [])
        self.error = error
        self.calls = []

    def rerank(
        self,
        *,
        query,
        documents,
        top_n,
    ):
        self.calls.append(
            {
                "query": query,
                "documents": list(documents),
                "top_n": top_n,
            }
        )

        if self.error is not None:
            raise self.error

        return self.results


def build_service(
    hits=None,
    rerank_client=None,
):
    embedding_client = FakeEmbeddingClient()
    vector_store = FakeVectorStore(hits)

    service = RetrievalService(
        embedding_client=embedding_client,
        vector_store=vector_store,
        rerank_client=rerank_client,
    )

    return (
        service,
        embedding_client,
        vector_store,
    )


def build_hit(
    *,
    index: int,
    similarity_score: float,
    document: str,
):
    return VectorSearchHit(
        vector_id=(
            f"doc_101_v_1_idx_{index}"
        ),
        distance=1.0 - similarity_score,
        similarity_score=similarity_score,
        document=document,
        metadata={
            "docId": 101,
            "kbId": 10,
            "versionNo": 1,
            "chunkIndex": index,
        },
    )


def test_empty_doc_ids_returns_empty_without_searching():
    service, embedding_client, vector_store = (
        build_service()
    )

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "锅炉水压试验条件",
            "docIds": [],
            "topK": 5,
        }
    )

    response = service.search(request)

    assert response.candidates == []
    assert embedding_client.received_queries == []
    assert vector_store.calls == []


def test_vector_search_passes_java_filtered_ids():
    hits = [
        build_hit(
            index=1,
            similarity_score=0.9,
            document=(
                "锅炉水压试验前应完成检查。"
            ),
        )
    ]

    service, embedding_client, vector_store = (
        build_service(hits)
    )

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "锅炉水压试验条件",
            "knowledgeBaseIds": [10],
            "docIds": [101, 102],
            "mode": "VECTOR_ONLY",
            "topK": 5,
            "candidateK": 50,
            "similarityThreshold": 0.3,
        }
    )

    response = service.search(request)

    assert embedding_client.received_queries == [
        "锅炉水压试验条件"
    ]

    assert len(vector_store.calls) == 1
    assert vector_store.calls[0]["doc_ids"] == [
        101,
        102,
    ]
    assert vector_store.calls[0]["kb_ids"] == [10]
    assert vector_store.calls[0]["top_k"] == 50

    assert len(response.candidates) == 1
    assert response.candidates[0].vector_id == (
        "doc_101_v_1_idx_1"
    )
    assert (
        response.candidates[0].similarity_score
        == pytest.approx(0.9)
    )


def test_similarity_threshold_filters_low_scores():
    hits = [
        build_hit(
            index=1,
            similarity_score=0.9,
            document="高相关内容",
        ),
        build_hit(
            index=2,
            similarity_score=0.2,
            document="低相关内容",
        ),
    ]

    service, _, _ = build_service(hits)

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "测试问题",
            "docIds": [101],
            "topK": 5,
            "candidateK": 20,
            "similarityThreshold": 0.5,
        }
    )

    response = service.search(request)

    assert len(response.candidates) == 1
    assert response.candidates[0].vector_id == (
        "doc_101_v_1_idx_1"
    )


def test_top_k_limits_vector_result_count():
    hits = [
        build_hit(
            index=index,
            similarity_score=0.9,
            document=f"内容{index}",
        )
        for index in range(1, 6)
    ]

    service, _, _ = build_service(hits)

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "测试问题",
            "docIds": [101],
            "mode": "VECTOR_ONLY",
            "topK": 2,
            "candidateK": 10,
        }
    )

    response = service.search(request)

    assert len(response.candidates) == 2

    assert [
        candidate.vector_id
        for candidate in response.candidates
    ] == [
        "doc_101_v_1_idx_1",
        "doc_101_v_1_idx_2",
    ]


def test_vector_rerank_returns_real_scores_and_order():
    hits = [
        build_hit(
            index=1,
            similarity_score=0.91,
            document="向量排名第一的内容",
        ),
        build_hit(
            index=2,
            similarity_score=0.86,
            document="重排序后最相关的内容",
        ),
        build_hit(
            index=3,
            similarity_score=0.80,
            document="重排序后第二相关的内容",
        ),
    ]

    rerank_client = FakeRerankClient(
        results=[
            RerankResult(
                index=1,
                score=0.97,
            ),
            RerankResult(
                index=2,
                score=0.82,
            ),
            RerankResult(
                index=0,
                score=0.40,
            ),
        ]
    )

    service, _, _ = build_service(
        hits,
        rerank_client=rerank_client,
    )

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "测试问题",
            "docIds": [101],
            "mode": "VECTOR_RERANK",
            "topK": 2,
            "candidateK": 20,
            "rerankTopN": 3,
            "rerankThreshold": 0.5,
        }
    )

    response = service.search(request)

    assert response.rerank_applied is True
    assert response.degraded_mode is None

    assert len(rerank_client.calls) == 1

    assert rerank_client.calls[0]["documents"] == [
        "向量排名第一的内容",
        "重排序后最相关的内容",
        "重排序后第二相关的内容",
    ]

    assert rerank_client.calls[0]["top_n"] == 3

    assert [
        candidate.vector_id
        for candidate in response.candidates
    ] == [
        "doc_101_v_1_idx_2",
        "doc_101_v_1_idx_3",
    ]

    assert (
        response.candidates[0].rerank_score
        == pytest.approx(0.97)
    )
    assert (
        response.candidates[1].rerank_score
        == pytest.approx(0.82)
    )


def test_rerank_threshold_filters_low_scores():
    hits = [
        build_hit(
            index=1,
            similarity_score=0.9,
            document="候选一",
        ),
        build_hit(
            index=2,
            similarity_score=0.8,
            document="候选二",
        ),
    ]

    rerank_client = FakeRerankClient(
        results=[
            RerankResult(
                index=0,
                score=0.91,
            ),
            RerankResult(
                index=1,
                score=0.20,
            ),
        ]
    )

    service, _, _ = build_service(
        hits,
        rerank_client=rerank_client,
    )

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "测试问题",
            "docIds": [101],
            "mode": "VECTOR_RERANK",
            "topK": 5,
            "rerankTopN": 2,
            "rerankThreshold": 0.5,
        }
    )

    response = service.search(request)

    assert response.rerank_applied is True
    assert len(response.candidates) == 1

    assert response.candidates[0].vector_id == (
        "doc_101_v_1_idx_1"
    )


def test_rerank_failure_degrades_without_fake_scores():
    hits = [
        build_hit(
            index=1,
            similarity_score=0.9,
            document="测试内容一",
        ),
        build_hit(
            index=2,
            similarity_score=0.8,
            document="测试内容二",
        ),
    ]

    rerank_client = FakeRerankClient(
        error=RuntimeError(
            "模拟 Rerank 服务不可用"
        )
    )

    service, _, _ = build_service(
        hits,
        rerank_client=rerank_client,
    )

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "测试问题",
            "docIds": [101],
            "mode": "VECTOR_RERANK",
            "topK": 2,
        }
    )

    response = service.search(request)

    assert response.rerank_applied is False
    assert response.degraded_mode == "VECTOR_ONLY"
    assert len(response.candidates) == 2

    assert all(
        candidate.rerank_score is None
        for candidate in response.candidates
    )


def test_missing_rerank_client_degrades_to_vector_only():
    hits = [
        build_hit(
            index=1,
            similarity_score=0.9,
            document="测试内容",
        )
    ]

    service, _, _ = build_service(
        hits,
        rerank_client=None,
    )

    request = RetrievalSearchRequest.model_validate(
        {
            "query": "测试问题",
            "docIds": [101],
            "mode": "VECTOR_RERANK",
            "topK": 5,
        }
    )

    response = service.search(request)

    assert response.rerank_applied is False
    assert response.degraded_mode == "VECTOR_ONLY"
    assert len(response.candidates) == 1
    assert response.candidates[0].rerank_score is None