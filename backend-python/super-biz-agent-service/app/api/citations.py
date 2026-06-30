"""Citation API."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.db.session import get_db
from app.models.orm.user import User
from app.services.citation_service import CitationService

router = APIRouter()


@router.get("/messages/{message_id}/citations")
def list_citations(message_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    service = CitationService(db)
    rows = service.list_for_message(user.id, message_id)
    return {
        "code": 200,
        "message": "success",
        "data": [service.serialize(row) for row in rows],
    }
