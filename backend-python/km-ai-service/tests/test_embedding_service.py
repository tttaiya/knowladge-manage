from __future__ import annotations

from typing import List, Sequence

import pytest

from app.schemas.retrieval import EmbedRequest
from app.services.embeddings import (
    EmbeddingRequestError,
    EmbeddingService,
)


class FakeEmbeddingClient:
    """不访问阿里云的本地假 Embedding 客户端。"""

    def __init__(self) -> None:
        self.received_texts: List[str] = []

    def embed_texts(
        self,
        texts: Sequence[str],
    ) -> List[List[float]]:
        self.received_texts = list(texts)

        return [
            [
                float(index + 1),
                0.0,
                0.0,
            ]
            for index, _ in enumerate(texts)
        ]


class FakeVectorStore:
    """记录 EmbeddingService 传给 ChromaStore 的数据。"""

    def __init__(self) -> None:
        self.vector_ids = []
        self.embeddings = []
        self.documents = []
        self.metadatas = []

    def upsert_chunks(
        self,
        *,
        vector_ids,
        embeddings,
        documents,
        metadatas,
    ) -> int:
        self.vector_ids = list(vector_ids)
        self.embeddings = list(embeddings)
        self.documents = list(documents)
        self.metadatas = list(metadatas)

        return len(self.vector_ids)


def build_service():
    embedding_client = FakeEmbeddingClient()
    vector_store = FakeVectorStore()

    service = EmbeddingService(
        embedding_client=embedding_client,
        vector_store=vector_store,
    )

    return (
        service,
        embedding_client,
        vector_store,
    )


def test_embed_generates_vector_ids_and_writes_vectors():
    service, embedding_client, vector_store = (
        build_service()
    )

    request = EmbedRequest.model_validate(
        {
            "operation": "EMBED",
            "task": {
                "taskId": 9001,
                "docId": 101,
                "kbId": 10,
                "taskType": "PROCESS",
                "traceId": "trace-001",
                "targetVersionNo": 1,
            },
            "chunks": [
                {
                    "chunkIndex": 1,
                    "content": "锅炉水压试验前应完成检查。",
                    "chapterPath": "第五章/5.2",
                    "pageNo": 8,
                    "chunkType": "TEXT",
                    "charCount": 15,
                },
                {
                    "chunkIndex": 2,
                    "content": "试验期间应记录压力变化。",
                    "chapterPath": "第五章/5.2",
                    "pageNo": 9,
                    "chunkType": "TEXT",
                    "charCount": 13,
                },
            ],
        }
    )

    response = service.process(request)

    assert response.success is True
    assert response.doc_id == 101
    assert response.vector_ids == [
        "doc_101_v_1_idx_1",
        "doc_101_v_1_idx_2",
    ]

    assert embedding_client.received_texts == [
        "锅炉水压试验前应完成检查。",
        "试验期间应记录压力变化。",
    ]

    assert vector_store.vector_ids == [
        "doc_101_v_1_idx_1",
        "doc_101_v_1_idx_2",
    ]
    assert vector_store.documents == (
        embedding_client.received_texts
    )

    assert vector_store.metadatas[0] == {
        "docId": 101,
        "kbId": 10,
        "versionNo": 1,
        "chunkIndex": 1,
        "contentVersion": 1,
        "vectorId": "doc_101_v_1_idx_1",
    }

    assert response.chunks[0].vector_id == (
        "doc_101_v_1_idx_1"
    )
    assert response.chunks[0].chapter_path == (
        "第五章/5.2"
    )


def test_reembed_uses_original_vector_id():
    service, _, vector_store = build_service()

    request = EmbedRequest.model_validate(
        {
            "operation": "REEMBED",
            "task": {
                "taskId": 2001,
                "docId": 101,
                "kbId": 10,
                "taskType": "REEMBED",
                "traceId": "trace-reembed",
                "targetVersionNo": 1,
            },
            "chunks": [
                {
                    "chunkId": 501,
                    "docId": 101,
                    "kbId": 10,
                    "versionNo": 1,
                    "chunkIndex": 3,
                    "content": "编辑后的切片正文",
                    "vectorId": "doc_101_v_1_idx_3",
                    "contentVersion": 2,
                }
            ],
        }
    )

    response = service.process(request)

    assert response.success is True
    assert response.vector_ids == [
        "doc_101_v_1_idx_3"
    ]
    assert vector_store.vector_ids == [
        "doc_101_v_1_idx_3"
    ]
    assert vector_store.metadatas[0][
        "contentVersion"
    ] == 2

    assert response.chunks[0].chunk_id == 501
    assert response.chunks[0].content_version == 2


def test_reembed_without_vector_id_is_rejected():
    service, _, _ = build_service()

    request = EmbedRequest.model_validate(
        {
            "operation": "REEMBED",
            "task": {
                "docId": 101,
                "kbId": 10,
                "targetVersionNo": 1,
            },
            "chunks": [
                {
                    "chunkIndex": 3,
                    "content": "编辑后的正文",
                    "contentVersion": 2,
                }
            ],
        }
    )

    with pytest.raises(
        EmbeddingRequestError,
        match="必须提供原 vectorId",
    ):
        service.process(request)


def test_reembed_wrong_vector_id_is_rejected():
    service, _, _ = build_service()

    request = EmbedRequest.model_validate(
        {
            "operation": "REEMBED",
            "task": {
                "docId": 101,
                "kbId": 10,
                "targetVersionNo": 1,
            },
            "chunks": [
                {
                    "chunkIndex": 3,
                    "content": "编辑后的正文",
                    "vectorId": "doc_999_v_1_idx_3",
                    "contentVersion": 2,
                }
            ],
        }
    )

    with pytest.raises(
        EmbeddingRequestError,
        match="不符合公共命名规则",
    ):
        service.process(request)


def test_chunk_doc_id_must_match_task_doc_id():
    service, _, _ = build_service()

    request = EmbedRequest.model_validate(
        {
            "operation": "EMBED",
            "task": {
                "docId": 101,
                "kbId": 10,
                "targetVersionNo": 1,
            },
            "chunks": [
                {
                    "docId": 202,
                    "chunkIndex": 1,
                    "content": "错误文档切片",
                }
            ],
        }
    )

    with pytest.raises(
        EmbeddingRequestError,
        match="chunk.docId 与 task.docId 不一致",
    ):
        service.process(request)


def test_chunk_kb_id_must_match_task_kb_id():
    service, _, _ = build_service()

    request = EmbedRequest.model_validate(
        {
            "operation": "EMBED",
            "task": {
                "docId": 101,
                "kbId": 10,
                "targetVersionNo": 1,
            },
            "chunks": [
                {
                    "kbId": 20,
                    "chunkIndex": 1,
                    "content": "错误知识库切片",
                }
            ],
        }
    )

    with pytest.raises(
        EmbeddingRequestError,
        match="chunk.kbId 与 task.kbId 不一致",
    ):
        service.process(request)