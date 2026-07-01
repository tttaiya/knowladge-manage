from __future__ import annotations

import json

import httpx
import pytest

from app.services.embeddings import (
    EmbeddingClient,
    EmbeddingConfigError,
    EmbeddingError,
    EmbeddingResponseError,
)


def build_client(handler) -> EmbeddingClient:
    transport = httpx.MockTransport(handler)
    http_client = httpx.Client(transport=transport)

    return EmbeddingClient(
        api_key="test-api-key",
        dimension=1024,
        batch_size=16,
        http_client=http_client,
    )


def test_embed_texts_success_and_preserves_order():
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.method == "POST"
        assert request.url.path.endswith(
            "/compatible-mode/v1/embeddings"
        )
        assert request.headers["Authorization"] == (
            "Bearer test-api-key"
        )

        payload = json.loads(request.content.decode("utf-8"))

        assert payload["model"] == "text-embedding-v4"
        assert payload["input"] == ["第一个切片", "第二个切片"]
        assert payload["dimensions"] == 1024

        # 特意倒序返回，验证客户端会按 index 恢复输入顺序。
        return httpx.Response(
            200,
            json={
                "data": [
                    {
                        "index": 1,
                        "embedding": [0.2] * 1024,
                    },
                    {
                        "index": 0,
                        "embedding": [0.1] * 1024,
                    },
                ]
            },
        )

    client = build_client(handler)

    try:
        vectors = client.embed_texts(
            ["第一个切片", "第二个切片"]
        )
    finally:
        client._http_client.close()

    assert len(vectors) == 2
    assert len(vectors[0]) == 1024
    assert vectors[0][0] == pytest.approx(0.1)
    assert vectors[1][0] == pytest.approx(0.2)


def test_embed_query_returns_single_vector():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "data": [
                    {
                        "index": 0,
                        "embedding": [0.3] * 1024,
                    }
                ]
            },
        )

    client = build_client(handler)

    try:
        vector = client.embed_query("锅炉水压试验")
    finally:
        client._http_client.close()

    assert len(vector) == 1024
    assert vector[0] == pytest.approx(0.3)


def test_missing_api_key_is_rejected():
    client = EmbeddingClient(
        api_key="",
        dimension=1024,
    )

    try:
        with pytest.raises(
            EmbeddingConfigError,
            match="DASHSCOPE_API_KEY",
        ):
            client.embed_texts(["测试文本"])
    finally:
        client.close()


def test_wrong_vector_dimension_is_rejected():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "data": [
                    {
                        "index": 0,
                        "embedding": [0.1] * 10,
                    }
                ]
            },
        )

    client = build_client(handler)

    try:
        with pytest.raises(
            EmbeddingResponseError,
            match="向量维度不正确",
        ):
            client.embed_texts(["测试文本"])
    finally:
        client._http_client.close()


def test_http_error_is_not_treated_as_success():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            401,
            json={
                "error": {
                    "message": "Invalid API key"
                }
            },
        )

    client = build_client(handler)

    try:
        with pytest.raises(
            EmbeddingError,
            match="HTTP 401",
        ):
            client.embed_texts(["测试文本"])
    finally:
        client._http_client.close()


def test_empty_text_is_rejected():
    client = EmbeddingClient(
        api_key="test-api-key",
        dimension=1024,
    )

    try:
        with pytest.raises(
            ValueError,
            match="文本不能为空",
        ):
            client.embed_texts(["   "])
    finally:
        client.close()