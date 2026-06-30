"""Knowledge repository."""

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument


class KnowledgeRepository:
    def __init__(self, db: Session):
        self.db = db

    def create_base(self, **values) -> KnowledgeBase:
        row = KnowledgeBase(**values)
        self.db.add(row)
        self.db.flush()
        return row

    def list_bases(self) -> list[KnowledgeBase]:
        return list(
            self.db.scalars(
                select(KnowledgeBase)
                .where(KnowledgeBase.deleted_at.is_(None), KnowledgeBase.status != "deleted")
                .order_by(KnowledgeBase.created_at.desc())
            )
        )

    def get_base(self, kb_id: str) -> KnowledgeBase | None:
        return self.db.get(KnowledgeBase, kb_id)

    def get_base_by_code(self, code: str) -> KnowledgeBase | None:
        return self.db.scalar(select(KnowledgeBase).where(KnowledgeBase.code == code, KnowledgeBase.deleted_at.is_(None)))

    def get_document(self, doc_id: str) -> KnowledgeDocument | None:
        return self.db.get(KnowledgeDocument, doc_id)

    def list_documents(self, kb_id: str) -> list[KnowledgeDocument]:
        return list(self.db.scalars(select(KnowledgeDocument).where(KnowledgeDocument.knowledge_base_id == kb_id, KnowledgeDocument.deleted_at.is_(None))))

    def list_chunks(self, kb_id: str) -> list[KnowledgeChunk]:
        return list(self.db.scalars(select(KnowledgeChunk).where(KnowledgeChunk.knowledge_base_id == kb_id, KnowledgeChunk.deleted_at.is_(None))))

    def list_chunks_by_document(self, doc_id: str) -> list[KnowledgeChunk]:
        return list(self.db.scalars(select(KnowledgeChunk).where(KnowledgeChunk.document_id == doc_id, KnowledgeChunk.deleted_at.is_(None))))

    def list_active_chunk_records_by_ids(self, chunk_ids: list[str]) -> list[tuple[KnowledgeChunk, KnowledgeDocument, KnowledgeBase]]:
        if not chunk_ids:
            return []
        stmt = (
            select(KnowledgeChunk, KnowledgeDocument, KnowledgeBase)
            .join(KnowledgeDocument, KnowledgeChunk.document_id == KnowledgeDocument.id)
            .join(KnowledgeBase, KnowledgeChunk.knowledge_base_id == KnowledgeBase.id)
            .where(
                KnowledgeChunk.id.in_(chunk_ids),
                KnowledgeChunk.deleted_at.is_(None),
                KnowledgeDocument.deleted_at.is_(None),
                KnowledgeDocument.status == "indexed",
                KnowledgeBase.deleted_at.is_(None),
                KnowledgeBase.status == "enabled",
            )
        )
        return list(self.db.execute(stmt).all())

    def add_document(self, **values) -> KnowledgeDocument:
        row = KnowledgeDocument(**values)
        self.db.add(row)
        self.db.flush()
        return row

    def add_chunk(self, **values) -> KnowledgeChunk:
        row = KnowledgeChunk(**values)
        self.db.add(row)
        self.db.flush()
        return row
