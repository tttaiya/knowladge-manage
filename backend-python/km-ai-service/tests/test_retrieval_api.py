from __future__ import annotations

from fastapi.testclient import TestClient

from app import middleware
from app.api.retrieval import (
    get_embedding_service,
    get_retrieval_service,
)
from app.main import app
from app.schemas.retrieval import (
    EmbedResponse,
    EmbeddedChunk,
    RetrievalCandidate,
    RetrievalSearchResponse,
)


class SuccessfulFakeEmbeddingService:
    """模拟向量化成功，不访问阿里云和 ChromaDB。"""

    def process(self, request):
        vector_id = (
            f"doc_{request.task.doc_id}"
            f"_v_{request.task.target_version_no}"
            f"_idx_{request.chunks[0].chunk_index}"
        )

        return EmbedResponse(
            success=True,
            docId=request.task.doc_id,
            vectorIds=[vector_id],
            chunks=[
                EmbeddedChunk(
                    docId=request.task.doc_id,
                    kbId=request.task.kb_id,
                    versionNo=(
                        request.task.target_version_no
                    ),
                    chunkIndex=(
                        request.chunks[0].chunk_index
                    ),
                    content=request.chunks[0].content,
                    vectorId=vector_id,
                    contentVersion=1,
                )
            ],
        )


class FailingFakeEmbeddingService:
    """模拟 Embedding 模型或 ChromaDB 调用失败。"""

    def process(self, request):
        raise RuntimeError(
            "模拟 Embedding 服务不可用"
        )


class SuccessfulFakeRetrievalService:
    """模拟纯向量检索成功。"""

    def search(self, request):
        return RetrievalSearchResponse(
            candidates=[
                RetrievalCandidate(
                    vectorId="doc_101_v_1_idx_1",
                    distance=0.1,
                    similarityScore=0.9,
                )
            ],
            elapsedMs=12,
            rerankApplied=False,
        )


class FailingFakeRetrievalService:
    """模拟查询 Embedding 或 ChromaDB 故障。"""

    def search(self, request):
        raise RuntimeError(
            "模拟 ChromaDB 不可用"
        )


def get_token_headers():
    """
    使用测试环境中已经配置的内部 Token。

    tests/conftest.py 会负责设置测试 Token。
    """

    assert middleware.INTERNAL_TOKEN

    return {
        "X-Internal-Token": middleware.INTERNAL_TOKEN,
    }


def build_embed_request_body():
    """构造 Worker 调用 /internal/ai/embed 的请求体。"""

    return {
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
            }
        ],
    }


def build_search_request_body():
    """
    构造 Java km-search-service 调用
    /internal/ai/retrieval/search 的请求体。
    """

    return {
        "query": "锅炉水压试验前需要满足哪些条件？",
        "knowledgeBaseIds": [10],
        "docIds": [101, 102],
        "tags": ["锅炉"],
        "mode": "VECTOR_ONLY",
        "topK": 5,
        "candidateK": 50,
        "similarityThreshold": 0.3,
        "rerankTopN": 10,
        "rerankThreshold": 0.5,
    }


def test_embed_api_returns_worker_compatible_success():
    """
    验证向量化成功响应包含：
    success、docId、vectorIds 和 chunks。
    """

    app.dependency_overrides[
        get_embedding_service
    ] = lambda: SuccessfulFakeEmbeddingService()

    client = TestClient(app)

    try:
        response = client.post(
            "/internal/ai/embed",
            headers=get_token_headers(),
            json=build_embed_request_body(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["success"] is True
    assert body["docId"] == 101
    assert body["vectorIds"] == [
        "doc_101_v_1_idx_1"
    ]

    assert body["chunks"][0]["vectorId"] == (
        "doc_101_v_1_idx_1"
    )
    assert body["chunks"][0]["chunkIndex"] == 1


def test_embed_api_returns_real_failure_body():
    """
    验证向量化失败时不能返回假成功。

    必须返回：
    success=false
    errorStage=EMBED
    errorMessage=真实错误信息
    """

    app.dependency_overrides[
        get_embedding_service
    ] = lambda: FailingFakeEmbeddingService()

    client = TestClient(app)

    try:
        response = client.post(
            "/internal/ai/embed",
            headers=get_token_headers(),
            json=build_embed_request_body(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["success"] is False
    assert body["docId"] == 101
    assert body["errorStage"] == "EMBED"

    assert (
        "模拟 Embedding 服务不可用"
        in body["errorMessage"]
    )


def test_retrieval_search_api_returns_vector_id():
    """
    验证检索接口返回 vectorId。

    F5 不再要求提前知道数据库自增 chunkId。
    """

    app.dependency_overrides[
        get_retrieval_service
    ] = lambda: SuccessfulFakeRetrievalService()

    client = TestClient(app)

    try:
        response = client.post(
            "/internal/ai/retrieval/search",
            headers=get_token_headers(),
            json=build_search_request_body(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["elapsedMs"] == 12
    assert body["rerankApplied"] is False
    assert len(body["candidates"]) == 1

    candidate = body["candidates"][0]

    assert candidate["vectorId"] == (
        "doc_101_v_1_idx_1"
    )
    assert candidate["distance"] == 0.1
    assert candidate["similarityScore"] == 0.9

    # Java Search 后续使用 vectorId 回查真实 chunkId。
    assert "chunkId" not in candidate


def test_retrieval_search_api_returns_502_on_failure():
    """
    验证真实检索故障返回 HTTP 502，
    而不是返回空结果或假成功。
    """

    app.dependency_overrides[
        get_retrieval_service
    ] = lambda: FailingFakeRetrievalService()

    client = TestClient(app)

    try:
        response = client.post(
            "/internal/ai/retrieval/search",
            headers=get_token_headers(),
            json=build_search_request_body(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 502

    body = response.json()

    assert (
        "模拟 ChromaDB 不可用"
        in body["detail"]
    )