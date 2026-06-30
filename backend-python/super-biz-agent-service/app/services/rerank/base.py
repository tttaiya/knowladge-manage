"""Reranker interfaces."""

from dataclasses import dataclass


@dataclass
class RerankResult:
    chunk_id: str
    score: float


class BaseReranker:
    async def rerank(self, query: str, chunks: list) -> list[RerankResult]:
        raise NotImplementedError
