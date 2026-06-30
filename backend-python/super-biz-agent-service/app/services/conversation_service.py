"""Conversation service."""

from datetime import datetime, timezone

from fastapi import HTTPException
from sqlalchemy.orm import Session

from app.models.orm.conversation import ChatMessage, ChatSession
from app.repositories.conversation_repository import ConversationRepository


def make_title(question: str) -> str:
    title = " ".join(question.split())
    if not title:
        return "新会话"
    return title[:30] + ("..." if len(title) > 30 else "")


class ConversationService:
    def __init__(self, db: Session):
        self.db = db
        self.repo = ConversationRepository(db)

    def create_session(self, user_id: str, title: str | None = None, session_id: str | None = None) -> ChatSession:
        row = self.repo.create_session(user_id, title or "新会话", session_id=session_id)
        self.db.commit()
        self.db.refresh(row)
        return row

    def ensure_session(self, user_id: str, session_id: str | None, question: str | None = None) -> tuple[ChatSession, bool]:
        if session_id:
            existing = self.repo.get_session(user_id, session_id)
            if existing:
                return existing, False
            row = self.repo.create_session(user_id, title=make_title(question or ""), session_id=session_id)
            self.db.flush()
            return row, True
        row = self.repo.create_session(user_id, title=make_title(question or ""))
        self.db.flush()
        return row, True

    def list_sessions(self, user_id: str) -> list[ChatSession]:
        return self.repo.list_sessions(user_id)

    def get_session(self, user_id: str, session_id: str) -> ChatSession:
        row = self.repo.get_session(user_id, session_id)
        if row is None:
            raise HTTPException(status_code=404, detail="会话不存在")
        return row

    def list_messages(self, user_id: str, session_id: str) -> list[ChatMessage]:
        self.get_session(user_id, session_id)
        return self.repo.list_messages(user_id, session_id)

    def update_title(self, user_id: str, session_id: str, title: str) -> ChatSession:
        row = self.get_session(user_id, session_id)
        row.title = title
        self.db.commit()
        self.db.refresh(row)
        return row

    def delete_session(self, user_id: str, session_id: str) -> None:
        row = self.get_session(user_id, session_id)
        row.status = "deleted"
        row.deleted_at = datetime.now(timezone.utc)
        self.db.commit()

    def add_message(self, user_id: str, session_id: str, role: str, content: str, **kwargs) -> ChatMessage:
        row = self.repo.add_message(user_id, session_id, role, content, **kwargs)
        self.db.flush()
        return row

    def update_message(self, message: ChatMessage, **values) -> ChatMessage:
        return self.repo.update_message(message, **values)
