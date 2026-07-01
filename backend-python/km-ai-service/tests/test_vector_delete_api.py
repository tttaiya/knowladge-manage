from __future__ import annotations

from fastapi.testclient import TestClient

from app import middleware
from app.api.retrieval import get_vector_store
from app.main import app


class SuccessfulFakeVectorStore:
    """模拟 ChromaDB 删除成功。"""

    def __init__(self) -> None:
        self.deleted_doc_ids = []
        self.deleted_versions = []

    def delete_document(
        self,
        doc_id: int,
    ) -> int:
        self.deleted_doc_ids.append(doc_id)

        # 模拟该文档共有三个向量。
        return 3

    def delete_document_version(
        self,
        doc_id: int,
        version_no: int,
    ) -> int:
        self.deleted_versions.append(
            (
                doc_id,
                version_no,
            )
        )

        # 模拟该版本共有两个向量。
        return 2


class FailingFakeVectorStore:
    """模拟 ChromaDB 删除故障。"""

    def delete_document(
        self,
        doc_id: int,
    ) -> int:
        raise RuntimeError(
            "模拟 ChromaDB 删除文档失败"
        )

    def delete_document_version(
        self,
        doc_id: int,
        version_no: int,
    ) -> int:
        raise RuntimeError(
            "模拟 ChromaDB 删除版本失败"
        )


def get_token_headers():
    assert middleware.INTERNAL_TOKEN

    return {
        "X-Internal-Token": middleware.INTERNAL_TOKEN,
    }


def test_delete_document_vectors_success():
    fake_store = SuccessfulFakeVectorStore()

    app.dependency_overrides[
        get_vector_store
    ] = lambda: fake_store

    client = TestClient(app)

    try:
        response = client.delete(
            "/internal/ai/vectors/101",
            headers=get_token_headers(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["success"] is True
    assert body["docId"] == 101
    assert body["deletedCount"] == 3

    assert fake_store.deleted_doc_ids == [
        101
    ]


def test_delete_document_version_vectors_success():
    fake_store = SuccessfulFakeVectorStore()

    app.dependency_overrides[
        get_vector_store
    ] = lambda: fake_store

    client = TestClient(app)

    try:
        response = client.delete(
            "/internal/ai/vectors/101/versions/2",
            headers=get_token_headers(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["success"] is True
    assert body["docId"] == 101
    assert body["versionNo"] == 2
    assert body["deletedCount"] == 2

    assert fake_store.deleted_versions == [
        (
            101,
            2,
        )
    ]


def test_delete_document_vectors_returns_real_failure():
    app.dependency_overrides[
        get_vector_store
    ] = lambda: FailingFakeVectorStore()

    client = TestClient(app)

    try:
        response = client.delete(
            "/internal/ai/vectors/101",
            headers=get_token_headers(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["success"] is False
    assert body["docId"] == 101
    assert body["deletedCount"] == 0
    assert body["errorStage"] == "CHROMA"

    assert (
        "模拟 ChromaDB 删除文档失败"
        in body["errorMessage"]
    )


def test_delete_version_vectors_returns_real_failure():
    app.dependency_overrides[
        get_vector_store
    ] = lambda: FailingFakeVectorStore()

    client = TestClient(app)

    try:
        response = client.delete(
            "/internal/ai/vectors/101/versions/2",
            headers=get_token_headers(),
        )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 200

    body = response.json()

    assert body["success"] is False
    assert body["docId"] == 101
    assert body["versionNo"] == 2
    assert body["deletedCount"] == 0
    assert body["errorStage"] == "CHROMA"

    assert (
        "模拟 ChromaDB 删除版本失败"
        in body["errorMessage"]
    )