"""Citation binding and lookup."""

from sqlalchemy.orm import Session

from app.models.orm.conversation import ChatMessageCitation
from app.repositories.conversation_repository import ConversationRepository


class CitationService:
    def __init__(self, db: Session):
        self.db = db
        self.repo = ConversationRepository(db)

    def persist(self, message_id: str, citations: list[dict]) -> list[ChatMessageCitation]:
        rows = []
        seen = set()
        next_index = 1
        for item in citations:
            chunk_id = item.get("chunk_id") or f"idx-{next_index}"
            if chunk_id in seen:
                continue
            seen.add(chunk_id)
            row = ChatMessageCitation(
                message_id=message_id,
                citation_index=next_index,
                knowledge_base_id=item.get("knowledge_base_id"),
                knowledge_base_name=item.get("knowledge_base_name"),
                document_id=item.get("document_id"),
                chunk_id=chunk_id,
                source_title=item.get("document_name") or item.get("source_title") or "",
                section_path=item.get("section_path"),
                content_preview=item.get("content_preview") or item.get("content") or "",
                vector_score=item.get("vector_score"),
                rerank_score=item.get("rerank_score"),
            )
            self.repo.add_citation(row)
            rows.append(row)
            next_index += 1
        return rows

    def list_for_message(self, user_id: str, message_id: str) -> list[ChatMessageCitation]:
        message = self.repo.get_message(user_id, message_id)
        if message is None:
            return []
        return self.repo.list_citations(message_id)

    def list_for_messages(self, message_ids: list[str]) -> dict[str, list[dict]]:
        grouped: dict[str, list[dict]] = {message_id: [] for message_id in message_ids}
        for row in self.repo.list_citations_for_messages(message_ids):
            grouped.setdefault(row.message_id, []).append(self.serialize(row))
        return grouped

    def serialize(self, row: ChatMessageCitation) -> dict:
        return {
            "index": row.citation_index,
            "knowledge_base_id": row.knowledge_base_id,
            "knowledge_base_name": row.knowledge_base_name,
            "document_id": row.document_id,
            "document_name": row.source_title,
            "chunk_id": row.chunk_id,
            "section_path": row.section_path,
            "content_preview": row.content_preview,
            "vector_score": row.vector_score,
            "rerank_score": row.rerank_score,
            "download_url": f"/api/knowledge/documents/{row.document_id}/download" if row.document_id else None,
        }
