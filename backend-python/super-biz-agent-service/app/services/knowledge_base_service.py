"""Knowledge base management service."""

from __future__ import annotations

from datetime import datetime
from pathlib import Path
import hashlib
import re

from fastapi import HTTPException, UploadFile
from sqlalchemy.orm import Session

from app.repositories.knowledge_repository import KnowledgeRepository
from app.services.document_splitter_service import document_splitter_service
from app.services.settings_service import SettingsService
from app.services.vector_index_service import vector_index_service

KB_UPLOAD_DIR = Path("uploads/knowledge")

TERM_STOPWORDS = {
    "知识库",
    "电力测试知识库",
    "电力行业知识库",
    "示例文档",
    "基础概念",
    "基础",
    "常见",
    "处理",
    "关注点",
    "用于",
    "完成",
    "以及",
    "包括",
    "相关",
    "当前",
    "文档",
}


class KnowledgeBaseService:
    def __init__(self, db: Session):
        self.db = db
        self.repo = KnowledgeRepository(db)

    def create(self, user_id: str, name: str, code: str, description: str | None = None):
        if self.repo.get_base_by_code(code):
            raise HTTPException(status_code=409, detail="知识库编码已存在")
        row = self.repo.create_base(name=name, code=code, description=description, created_by=user_id)
        self.db.commit()
        self.db.refresh(row)
        return row

    def list(self):
        return self.repo.list_bases()

    def update(self, kb_id: str, **values):
        row = self.repo.get_base(kb_id)
        if not row or row.deleted_at is not None:
            raise HTTPException(status_code=404, detail="知识库不存在")
        for key, value in values.items():
            if value is not None and hasattr(row, key):
                setattr(row, key, value)
        self.db.commit()
        self.db.refresh(row)
        return row

    def delete(self, kb_id: str):
        row = self.repo.get_base(kb_id)
        if not row or row.deleted_at is not None:
            raise HTTPException(status_code=404, detail="知识库不存在")
        now = datetime.utcnow()
        suffix = f"__deleted__{int(now.timestamp())}_{row.id[:8]}"
        row.code = f"{row.code[:120 - len(suffix)]}{suffix}"
        row.status = "deleted"
        row.deleted_at = now
        for doc in self.repo.list_documents(kb_id):
            doc.status = "deleted"
            doc.deleted_at = now
        for chunk in self.repo.list_chunks(kb_id):
            chunk.deleted_at = now
        _ = vector_index_service.delete_by_knowledge_base_id(kb_id)
        self.db.commit()

    async def upload_document(self, user_id: str, kb_id: str, file: UploadFile):
        kb = self.repo.get_base(kb_id)
        if not kb or kb.status == "deleted":
            raise HTTPException(status_code=404, detail="知识库不存在")
        content = await file.read()
        safe_name = Path(file.filename or "document.txt").name
        target_dir = KB_UPLOAD_DIR / kb_id
        target_dir.mkdir(parents=True, exist_ok=True)
        path = target_dir / safe_name
        path.write_bytes(content)
        checksum = hashlib.sha256(content).hexdigest()
        doc = self.repo.add_document(
            knowledge_base_id=kb_id,
            filename=safe_name,
            file_path=str(path),
            file_ext=path.suffix.lstrip(".").lower(),
            file_size=len(content),
            checksum=checksum,
            status="uploaded",
            uploaded_by=user_id,
        )
        text = content.decode("utf-8", errors="ignore")
        chunks = document_splitter_service.split_for_knowledge_base(text, str(path))
        chunk_rows = []
        for index, item in enumerate(chunks):
            chunk = self.repo.add_chunk(
                knowledge_base_id=kb_id,
                document_id=doc.id,
                section_path=item["section_path"],
                content=item["content"],
                chunk_index=index,
                token_count=max(1, len(item["content"]) // 2),
            )
            chunk_rows.append(chunk)
        try:
            indexed_ids = vector_index_service.index_chunks(chunk_rows, document_name=doc.filename)
            for chunk in chunk_rows:
                chunk.milvus_vector_id = indexed_ids.get(chunk.id)
            doc.error_message = None
        except Exception as exc:
            doc.error_message = f"Milvus 索引失败，已保留数据库切片用于兜底检索: {exc}"
        doc.chunk_count = len(chunks)
        doc.status = "indexed"
        self._merge_intent_terms(text)
        self.db.commit()
        self.db.refresh(doc)
        return doc

    def reindex_document_sections(self, doc_id: str):
        doc = self.repo.get_document(doc_id)
        if not doc or doc.deleted_at is not None:
            raise HTTPException(status_code=404, detail="文档不存在")
        path = Path(doc.file_path)
        text = path.read_text(encoding="utf-8", errors="ignore")
        now = datetime.utcnow()
        _ = vector_index_service.delete_by_document_id(doc.id)
        for chunk in self.repo.list_chunks_by_document(doc.id):
            chunk.deleted_at = now
        chunks = document_splitter_service.split_for_knowledge_base(text, str(path))
        chunk_rows = []
        for index, item in enumerate(chunks):
            chunk = self.repo.add_chunk(
                knowledge_base_id=doc.knowledge_base_id,
                document_id=doc.id,
                section_path=item["section_path"],
                content=item["content"],
                chunk_index=index,
                token_count=max(1, len(item["content"]) // 2),
            )
            chunk_rows.append(chunk)
        try:
            indexed_ids = vector_index_service.index_chunks(chunk_rows, document_name=doc.filename)
            for chunk in chunk_rows:
                chunk.milvus_vector_id = indexed_ids.get(chunk.id)
            doc.error_message = None
        except Exception as exc:
            doc.error_message = f"Milvus 重建索引失败，已保留数据库切片用于兜底检索: {exc}"
        doc.chunk_count = len(chunks)
        doc.status = "indexed"
        self._merge_intent_terms(text)
        self.db.commit()
        self.db.refresh(doc)
        return doc

    def _merge_intent_terms(self, text: str) -> None:
        service = SettingsService(self.db)
        service.initialize_defaults()
        current = service.get("intent.knowledge_terms", [])
        terms = set(term.strip().lower() for term in current if isinstance(term, str) and term.strip())
        terms.update(self._extract_terms(text))
        service.update("intent.knowledge_terms", sorted(terms)[:200])

    def _extract_terms(self, text: str) -> set[str]:
        terms: set[str] = set()
        for heading in re.findall(r"^#{1,6}\s+(.+)$", text, flags=re.MULTILINE):
            term = heading.strip()
            if self._is_valid_term(term):
                terms.add(term.lower())
        return terms

    def _is_valid_term(self, token: str) -> bool:
        if len(token) < 2 or len(token) > 12:
            return False
        if token.lower() in TERM_STOPWORDS:
            return False
        return True
