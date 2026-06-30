"""Conversation API."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.db.session import get_db
from app.models.orm.user import User
from app.models.schemas.conversation import ConversationCreateRequest, ConversationResponse, ConversationUpdateRequest, MessageResponse
from app.services.conversation_service import ConversationService

router = APIRouter(prefix="/conversations")


@router.post("", response_model=ConversationResponse)
def create_conversation(
    request: ConversationCreateRequest,
    user: User = Depends(require_login),
    db: Session = Depends(get_db),
):
    return ConversationService(db).create_session(user.id, request.title)


@router.get("", response_model=list[ConversationResponse])
def list_conversations(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return ConversationService(db).list_sessions(user.id)


@router.get("/{session_id}", response_model=ConversationResponse)
def get_conversation(session_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    return ConversationService(db).get_session(user.id, session_id)


@router.get("/{session_id}/messages", response_model=list[MessageResponse])
def list_messages(session_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    return ConversationService(db).list_messages(user.id, session_id)


@router.patch("/{session_id}", response_model=ConversationResponse)
def update_conversation(
    session_id: str,
    request: ConversationUpdateRequest,
    user: User = Depends(require_login),
    db: Session = Depends(get_db),
):
    return ConversationService(db).update_title(user.id, session_id, request.title)


@router.delete("/{session_id}")
def delete_conversation(session_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    ConversationService(db).delete_session(user.id, session_id)
    return {"code": 200, "message": "success", "data": None}
