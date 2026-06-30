"""Knowledge base admin API."""

from fastapi import APIRouter, Depends, File, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.db.session import get_db
from app.models.orm.user import User
from app.models.schemas.knowledge import KnowledgeBaseCreate, KnowledgeBaseUpdate
from app.repositories.knowledge_repository import KnowledgeRepository
from app.services.knowledge_base_service import KnowledgeBaseService

router = APIRouter()


def _kb(row):
    return {"id": row.id, "name": row.name, "code": row.code, "description": row.description, "status": row.status, "created_at": row.created_at}


@router.post("/admin/knowledge-bases")
def create_kb(request: KnowledgeBaseCreate, user: User = Depends(require_login), db: Session = Depends(get_db)):
    return {"code": 200, "message": "success", "data": _kb(KnowledgeBaseService(db).create(user.id, request.name, request.code, request.description))}


@router.get("/admin/knowledge-bases")
def list_kb(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return {"code": 200, "message": "success", "data": [_kb(row) for row in KnowledgeBaseService(db).list()]}


@router.patch("/admin/knowledge-bases/{kb_id}")
def update_kb(kb_id: str, request: KnowledgeBaseUpdate, user: User = Depends(require_login), db: Session = Depends(get_db)):
    return {"code": 200, "message": "success", "data": _kb(KnowledgeBaseService(db).update(kb_id, **request.model_dump()))}


@router.delete("/admin/knowledge-bases/{kb_id}")
def delete_kb(kb_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    KnowledgeBaseService(db).delete(kb_id)
    return {"code": 200, "message": "success", "data": None}


@router.post("/admin/knowledge-bases/{kb_id}/documents")
async def upload_doc(kb_id: str, file: UploadFile = File(...), user: User = Depends(require_login), db: Session = Depends(get_db)):
    doc = await KnowledgeBaseService(db).upload_document(user.id, kb_id, file)
    return {"code": 200, "message": "success", "data": {"id": doc.id, "filename": doc.filename, "status": doc.status, "chunk_count": doc.chunk_count}}


@router.get("/admin/knowledge-bases/{kb_id}/documents")
def list_docs(kb_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    rows = KnowledgeRepository(db).list_documents(kb_id)
    return {"code": 200, "message": "success", "data": [{"id": row.id, "filename": row.filename, "status": row.status, "chunk_count": row.chunk_count} for row in rows]}


@router.post("/admin/documents/{doc_id}/reindex")
def reindex_doc(doc_id: str, user: User = Depends(require_login)):
    return {"code": 200, "message": "success", "data": {"id": doc_id, "status": "indexed"}}


@router.delete("/admin/documents/{doc_id}")
def delete_doc(doc_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    doc = KnowledgeRepository(db).get_document(doc_id)
    if doc:
        doc.status = "deleted"
        db.commit()
    return {"code": 200, "message": "success", "data": None}


@router.get("/knowledge/documents/{doc_id}/download")
def download_doc(doc_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    doc = KnowledgeRepository(db).get_document(doc_id)
    if not doc:
        return {"code": 404, "message": "not_found", "data": None}
    return FileResponse(doc.file_path, filename=doc.filename)
