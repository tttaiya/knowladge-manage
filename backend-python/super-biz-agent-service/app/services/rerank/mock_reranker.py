"""Test-friendly reranker."""

from app.services.rerank.base import BaseReranker, RerankResult


class MockReranker(BaseReranker):
    async def rerank(self, query: str, chunks: list) -> list[RerankResult]:
        return [RerankResult(chunk_id=chunk.chunk_id, score=1.0 - index * 0.01) for index, chunk in enumerate(chunks)]
