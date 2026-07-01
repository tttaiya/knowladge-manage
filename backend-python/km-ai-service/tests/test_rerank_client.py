from __future__ import annotations

import json

import httpx
import pytest

from app.services.rerank import (
    RerankClient,
    RerankConfigError,
    RerankError,
    RerankResponseError,
)


def build_client(handler) -> RerankClient:
    transport = httpx.MockTransport(handler)
    http_client = httpx.Client(
        transport=transport,
    )

    return RerankClient(
        api_key="test-api-key",
        http_client=http_client,
    )


def test_rerank_success_and_parses_scores():
    def handler(
        request: httpx.Request,
    ) -> httpx.Response:
        assert request.method == "POST"
        assert request.url.path.endswith(
            "/compatible-api/v1/reranks"
        )

        assert request.headers[
            "Authorization"
        ] == "Bearer test-api-key"

        payload = json.loads(
            request.content.decode("utf-8")
        )

        assert payload["model"] == (
            "qwen3-rerank"
        )
        assert payload["query"] == (
            "锅炉水压试验需要满足什么条件？"
        )
        assert payload["documents"] == [
            "锅炉水压试验前应完成设备检查。",
            "汽轮机润滑油系统检查内容。",
            "水压试验期间应记录压力变化。",
        ]
        assert payload["top_n"] == 2
        assert "instruct" in payload

        return httpx.Response(
            200,
            json={
                "object": "list",
                "results": [
                    {
                        "index": 0,
                        "relevance_score": 0.95,
                    },
                    {
                        "index": 2,
                        "relevance_score": 0.78,
                    },
                ],
                "model": "qwen3-rerank",
            },
        )

    client = build_client(handler)

    try:
        results = client.rerank(
            query=(
                "锅炉水压试验需要满足什么条件？"
            ),
            documents=[
                "锅炉水压试验前应完成设备检查。",
                "汽轮机润滑油系统检查内容。",
                "水压试验期间应记录压力变化。",
            ],
            top_n=2,
        )
    finally:
        client._http_client.close()

    assert len(results) == 2

    assert results[0].index == 0
    assert results[0].score == pytest.approx(
        0.95
    )

    assert results[1].index == 2
    assert results[1].score == pytest.approx(
        0.78
    )


def test_empty_documents_returns_empty():
    client = RerankClient(
        api_key="test-api-key",
    )

    try:
        results = client.rerank(
            query="测试问题",
            documents=[],
            top_n=5,
        )
    finally:
        client.close()

    assert results == []


def test_missing_api_key_is_rejected():
    client = RerankClient(
        api_key="",
    )

    try:
        with pytest.raises(
            RerankConfigError,
            match="DASHSCOPE_API_KEY",
        ):
            client.rerank(
                query="测试问题",
                documents=["测试文档"],
                top_n=1,
            )
    finally:
        client.close()


def test_http_error_is_not_treated_as_success():
    def handler(
        request: httpx.Request,
    ) -> httpx.Response:
        return httpx.Response(
            401,
            json={
                "code": "InvalidApiKey",
                "message": (
                    "Invalid API-key provided."
                ),
            },
        )

    client = build_client(handler)

    try:
        with pytest.raises(
            RerankError,
            match="HTTP 401",
        ):
            client.rerank(
                query="测试问题",
                documents=["测试文档"],
                top_n=1,
            )
    finally:
        client._http_client.close()


def test_missing_results_is_rejected():
    def handler(
        request: httpx.Request,
    ) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "object": "list",
                "model": "qwen3-rerank",
            },
        )

    client = build_client(handler)

    try:
        with pytest.raises(
            RerankResponseError,
            match="缺少 results",
        ):
            client.rerank(
                query="测试问题",
                documents=["测试文档"],
                top_n=1,
            )
    finally:
        client._http_client.close()


def test_result_index_out_of_range_is_rejected():
    def handler(
        request: httpx.Request,
    ) -> httpx.Response:
        return httpx.Response(
            200,
            json={
                "results": [
                    {
                        "index": 9,
                        "relevance_score": 0.9,
                    }
                ]
            },
        )

    client = build_client(handler)

    try:
        with pytest.raises(
            RerankResponseError,
            match="index 超出",
        ):
            client.rerank(
                query="测试问题",
                documents=["唯一文档"],
                top_n=1,
            )
    finally:
        client._http_client.close()