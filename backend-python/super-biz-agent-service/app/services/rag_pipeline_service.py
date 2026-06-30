"""Configurable, testable RAG pipeline."""

from dataclasses import dataclass, field
import re
from typing import Any

from langchain_core.messages import HumanMessage, SystemMessage
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.llm_factory import llm_factory
from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument
from app.repositories.knowledge_repository import KnowledgeRepository
from app.services.settings_service import SettingsService
from app.services.vector_search_service import vector_search_service


@dataclass
class RetrievedChunk:
    content: str
    chunk_id: str
    document_id: str | None = None
    document_name: str = "知识库文档"
    knowledge_base_id: str | None = None
    knowledge_base_name: str | None = None
    section_path: str | None = None
    raw_score: float = 1.0
    normalized_score: float = 1.0
    rerank_score: float | None = None
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class RagResult:
    answer: str
    chunks: list[RetrievedChunk]
    citations: list[dict]
    is_knowledge_grounded: bool
    no_evidence: bool = False


class RagPipelineService:
    def __init__(self, db: Session | None = None, vector_search=None, reranker=None, llm=None):
        self.db = db
        self.vector_search = vector_search or self._default_vector_search
        self.reranker = reranker or llm_factory.get_rerank_client(db)
        self.llm = llm

    async def answer(self, question: str, knowledge_base_ids: list[str] | None = None) -> RagResult:
        chunks = self._deduplicate_chunks(await self.retrieve(question, knowledge_base_ids))
        if not chunks:
            if self._setting("rag.allow_fallback_answer", False):
                from app.services.general_chat_service import GeneralChatService

                fallback = await GeneralChatService(self.db).answer(question)
                return RagResult(answer=f"非知识库依据：{fallback}", chunks=[], citations=[], is_knowledge_grounded=False, no_evidence=True)
            return RagResult(answer="当前知识库未检索到足够依据", chunks=[], citations=[], is_knowledge_grounded=False, no_evidence=True)
        citations = self._build_citations(chunks)
        answer = await self._generate_answer(question, chunks)
        answer = self._normalize_answer_citations(answer, len(citations))
        return RagResult(answer=answer, chunks=chunks, citations=citations, is_knowledge_grounded=True)

    async def retrieve(self, question: str, knowledge_base_ids: list[str] | None = None) -> list[RetrievedChunk]:
        top_k = int(self._setting("rag.vector_top_k", 3))
        threshold = float(self._setting("rag.vector_score_threshold", 0.0))
        rerank_top_n = int(self._setting("rag.rerank_top_n", top_k))
        rerank_threshold = float(self._setting("rag.rerank_score_threshold", 0.0))
        rows = self.vector_search(question, top_k, knowledge_base_ids)
        raw_chunks = [self._coerce_chunk(row) for row in rows]
        chunks = self._validate_chunks_against_db(raw_chunks)
        chunks = [chunk for chunk in chunks if (not knowledge_base_ids or chunk.knowledge_base_id in knowledge_base_ids)]
        if self._setting("rag.require_keyword_overlap", False):
            chunks = [chunk for chunk in chunks if self._has_query_term_overlap(question, chunk.content)]
        chunks = [chunk for chunk in chunks if chunk.normalized_score >= threshold]
        if (
            not chunks
            and self.db is not None
            and raw_chunks
            and any(chunk.metadata.get("retrieval_source") != "db_fallback" for chunk in raw_chunks)
        ):
            chunks = self._db_chunk_search(question, top_k, knowledge_base_ids)
        if self._setting("rag.retrieval_mode", "vector") == "vector_rerank" and self.reranker and chunks:
            results = await self.reranker.rerank(question, chunks)
            scores = {item.chunk_id: item.score for item in results}
            for chunk in chunks:
                chunk.rerank_score = scores.get(chunk.chunk_id)
            chunks = [chunk for chunk in chunks if (chunk.rerank_score or 0) >= rerank_threshold]
            chunks.sort(key=lambda item: item.rerank_score or 0, reverse=True)
            return chunks[:rerank_top_n]
        return self._limit_per_knowledge_base(chunks, top_k, knowledge_base_ids)

    def _default_vector_search(self, question: str, top_k: int, knowledge_base_ids: list[str] | None = None) -> list[Any]:
        limit = top_k * max(1, len(knowledge_base_ids or []))
        try:
            return vector_search_service.search_similar_documents(question, limit, knowledge_base_ids=knowledge_base_ids)
        except Exception:
            return []

    async def _generate_answer(self, question: str, chunks: list[RetrievedChunk]) -> str:
        fallback = f"根据知识库资料，{chunks[0].content} [1]"
        model = self.llm
        if model is None:
            cfg = llm_factory.get_config(self.db)
            if not cfg.api_key:
                return fallback
            model = llm_factory.get_chat_model(self.db)

        context = "\n\n".join(
            f"[{index}] 文档：{chunk.document_name}\n章节：{chunk.section_path or '未标注'}\n内容：{chunk.content}"
            for index, chunk in enumerate(chunks, start=1)
        )
        system_prompt = (
            "你是一个严谨的知识库问答助手。只能依据用户提供的知识库片段回答，"
            "不要补充片段之外的事实。若片段不足以回答，回复“当前知识库未检索到足够依据”。"
            "回答中需要在关键结论后用 [1]、[2] 这样的编号标注引用。"
        )
        user_prompt = f"用户问题：{question}\n\n知识库片段：\n{context}"
        try:
            result = await model.ainvoke(
                [
                    SystemMessage(content=system_prompt),
                    HumanMessage(content=user_prompt),
                ]
            )
        except Exception:
            return fallback

        answer = str(getattr(result, "content", result) or "").strip()
        return answer or fallback

    def _build_citations(self, chunks: list[RetrievedChunk]) -> list[dict]:
        citations = []
        for index, chunk in enumerate(chunks, start=1):
            citations.append(
                {
                    "index": index,
                    "knowledge_base_id": chunk.knowledge_base_id,
                    "knowledge_base_name": chunk.knowledge_base_name,
                    "document_id": chunk.document_id,
                    "document_name": chunk.document_name,
                    "chunk_id": chunk.chunk_id,
                    "section_path": chunk.section_path,
                    "content_preview": chunk.content,
                    "vector_score": chunk.normalized_score,
                    "rerank_score": chunk.rerank_score,
                    "download_url": chunk.metadata.get("download_url"),
                }
            )
        return citations

    def _deduplicate_chunks(self, chunks: list[RetrievedChunk]) -> list[RetrievedChunk]:
        seen: set[str] = set()
        deduped: list[RetrievedChunk] = []
        for chunk in chunks:
            chunk_key = chunk.chunk_id or f"{chunk.document_id}:{chunk.section_path}:{chunk.content}"
            if chunk_key in seen:
                continue
            seen.add(chunk_key)
            deduped.append(chunk)
        return deduped

    def _normalize_answer_citations(self, answer: str, max_index: int) -> str:
        if not answer or max_index <= 0:
            return answer

        pattern = re.compile(r"\[(\d+)\]")
        normalized: list[str] = []
        cursor = 0
        last_valid_index: int | None = None
        last_marker_was_citation = False
        has_valid_citation = False

        for match in pattern.finditer(answer):
            text = answer[cursor : match.start()]
            if text:
                normalized.append(text)
                if text.strip():
                    last_marker_was_citation = False
                    last_valid_index = None

            index = int(match.group(1))
            if 1 <= index <= max_index:
                has_valid_citation = True
                if not (last_marker_was_citation and last_valid_index == index and (not text or text.isspace())):
                    normalized.append(f"[{index}]")
                last_marker_was_citation = True
                last_valid_index = index

            cursor = match.end()

        normalized.append(answer[cursor:])
        cleaned = "".join(normalized).strip()
        if has_valid_citation:
            return cleaned

        suffix = "" if cleaned.endswith(("。", "！", "？", ".", "!", "?", "]")) else " "
        return f"{cleaned}{suffix}[1]"

    def _db_chunk_search(self, question: str, top_k: int, knowledge_base_ids: list[str] | None = None) -> list[RetrievedChunk]:
        if self.db is None:
            return []
        stmt = (
            select(KnowledgeChunk, KnowledgeDocument, KnowledgeBase)
            .join(KnowledgeDocument, KnowledgeChunk.document_id == KnowledgeDocument.id)
            .join(KnowledgeBase, KnowledgeChunk.knowledge_base_id == KnowledgeBase.id)
            .where(
                KnowledgeChunk.deleted_at.is_(None),
                KnowledgeDocument.deleted_at.is_(None),
                KnowledgeDocument.status == "indexed",
                KnowledgeBase.status == "enabled",
            )
        )
        if knowledge_base_ids:
            stmt = stmt.where(KnowledgeChunk.knowledge_base_id.in_(knowledge_base_ids))
        terms = self._extract_query_terms(question)
        chunks = []
        for chunk, document, kb in self.db.execute(stmt).all():
            content_lower = chunk.content.lower()
            hits = sum(1 for term in terms if term in content_lower)
            if terms and hits == 0:
                continue
            normalized_score = hits / max(1, len(terms)) if terms else 0.5
            chunks.append(
                RetrievedChunk(
                    content=chunk.content,
                    chunk_id=chunk.id,
                    document_id=document.id,
                    document_name=document.filename,
                    knowledge_base_id=kb.id,
                    knowledge_base_name=kb.name,
                    section_path=chunk.section_path,
                    raw_score=normalized_score,
                    normalized_score=normalized_score,
                    metadata={"retrieval_source": "db_fallback"},
                )
            )
        chunks.sort(key=lambda item: item.normalized_score, reverse=True)
        return self._limit_per_knowledge_base(chunks, top_k, knowledge_base_ids)

    def _validate_chunks_against_db(self, chunks: list[RetrievedChunk]) -> list[RetrievedChunk]:
        if self.db is None or not chunks:
            return chunks
        external_chunks = [
            chunk
            for chunk in chunks
            if chunk.metadata.get("retrieval_source") == "knowledge_management"
        ]
        local_chunks = [
            chunk
            for chunk in chunks
            if chunk.metadata.get("retrieval_source") != "knowledge_management"
        ]
        chunk_ids = [chunk.chunk_id for chunk in local_chunks if chunk.chunk_id]
        if not chunk_ids:
            return external_chunks

        records = KnowledgeRepository(self.db).list_active_chunk_records_by_ids(chunk_ids)
        record_map = {
            chunk.id: (chunk, document, kb)
            for chunk, document, kb in records
        }
        validated: list[RetrievedChunk] = []
        for item in local_chunks:
            record = record_map.get(item.chunk_id)
            if record is None:
                continue
            chunk, document, kb = record
            merged_metadata = {
                **(item.metadata or {}),
                "chunk_id": chunk.id,
                "document_id": document.id,
                "knowledge_base_id": kb.id,
                "file_name": document.filename,
                "section_path": chunk.section_path,
            }
            validated.append(
                RetrievedChunk(
                    content=chunk.content,
                    chunk_id=chunk.id,
                    document_id=document.id,
                    document_name=document.filename,
                    knowledge_base_id=kb.id,
                    knowledge_base_name=kb.name,
                    section_path=chunk.section_path,
                    raw_score=item.raw_score,
                    normalized_score=item.normalized_score,
                    rerank_score=item.rerank_score,
                    metadata=merged_metadata,
                )
            )
        return external_chunks + validated

    def _extract_query_terms(self, question: str) -> list[str]:
        text = question.lower()
        domain_terms = [
            "dl/t",
            "gb/t",
            "继电保护",
            "验收",
            "电力",
            "技术标准",
            "变电站",
            "主变",
            "断路器",
            "隔离开关",
            "母线",
            "电流互感器",
            "电压互感器",
            "巡检",
            "标准",
            "规范",
            "规程",
            "内存",
            "使用率",
            "告警",
            "cpu",
            "磁盘",
            "服务",
            "不可用",
            "响应",
            "oom",
            "gc",
        ]
        matched_terms = self._matched_knowledge_terms(text)
        terms = matched_terms[:] if matched_terms else [term for term in domain_terms if term in text]
        terms.extend(re.findall(r"[a-z0-9]+(?:/[a-z0-9]+)?", text))
        for token in re.split(r"[\s,，。？?！!：:；;、/()（）]+", text):
            token = re.sub(r"^(告诉我|请问|帮我|说一下|介绍一下)", "", token)
            token = re.sub(r"(是什么|有哪些|有什么|怎么处理|如何处理|怎么办)$", "", token)
            if len(token) >= 2 and token not in terms:
                terms.append(token)
        return list(dict.fromkeys(terms))

    def _matched_knowledge_terms(self, text: str) -> list[str]:
        if not self.db:
            return []
        terms = SettingsService(self.db).get("intent.knowledge_terms", [])
        return [
            term.strip().lower()
            for term in terms
            if isinstance(term, str) and term.strip() and term.strip().lower() in text
        ]

    def _has_query_term_overlap(self, question: str, content: str) -> bool:
        terms = self._extract_query_terms(question)
        if not terms:
            return True
        content_lower = content.lower()
        return any(term in content_lower for term in terms)

    def _limit_per_knowledge_base(
        self,
        chunks: list[RetrievedChunk],
        top_k: int,
        knowledge_base_ids: list[str] | None = None,
    ) -> list[RetrievedChunk]:
        if not knowledge_base_ids:
            return chunks[:top_k]
        selected = []
        for kb_id in knowledge_base_ids:
            selected.extend([chunk for chunk in chunks if chunk.knowledge_base_id == kb_id][:top_k])
        return selected

    def _coerce_chunk(self, row) -> RetrievedChunk:
        if isinstance(row, RetrievedChunk):
            return row
        metadata = getattr(row, "metadata", {}) or {}
        content = getattr(row, "page_content", None) or getattr(row, "content", "")
        raw_score = float(getattr(row, "score", metadata.get("raw_score", metadata.get("score", 1.0))))
        normalized_score = float(metadata.get("normalized_score", 1.0 / (1.0 + max(raw_score, 0.0))))
        return RetrievedChunk(
            content=content,
            chunk_id=str(metadata.get("chunk_id") or metadata.get("id") or id(row)),
            document_id=metadata.get("document_id"),
            document_name=metadata.get("file_name") or metadata.get("source") or "知识库文档",
            knowledge_base_id=metadata.get("knowledge_base_id"),
            knowledge_base_name=metadata.get("knowledge_base_name"),
            section_path=metadata.get("section_path"),
            raw_score=raw_score,
            normalized_score=normalized_score,
            metadata=metadata,
        )

    def _setting(self, key: str, default):
        if not self.db:
            return default
        return SettingsService(self.db).get(key, default)
