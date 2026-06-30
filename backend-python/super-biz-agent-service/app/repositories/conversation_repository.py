"""Conversation repository."""

from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.orm.conversation import ChatMessage, ChatMessageCitation, ChatSession


class ConversationRepository:
    def __init__(self, db: Session):
        self.db = db

    def create_session(self, user_id: str, title: str = "新会话", session_id: str | None = None) -> ChatSession:
        row = ChatSession(id=session_id, user_id=user_id, title=title) if session_id else ChatSession(user_id=user_id, title=title)
        self.db.add(row)
        self.db.flush()
        return row

    def get_session(self, user_id: str, session_id: str) -> ChatSession | None:
        return self.db.scalar(
            select(ChatSession).where(
                ChatSession.id == session_id,
                ChatSession.user_id == user_id,
                ChatSession.status == "active",
                ChatSession.deleted_at.is_(None),
            )
        )

    def list_sessions(self, user_id: str) -> list[ChatSession]:
        return list(
            self.db.scalars(
                select(ChatSession)
                .where(ChatSession.user_id == user_id, ChatSession.status == "active", ChatSession.deleted_at.is_(None))
                .order_by(ChatSession.last_message_at.desc().nullslast(), ChatSession.updated_at.desc())
            )
        )

    def add_message(
        self,
        user_id: str,
        session_id: str,
        role: str,
        content: str,
        status: str = "completed",
        intent: str | None = None,
        is_knowledge_grounded: bool = False,
    ) -> ChatMessage:
        row = ChatMessage(
            user_id=user_id,
            session_id=session_id,
            role=role,
            content=content,
            status=status,
            intent=intent,
            is_knowledge_grounded=is_knowledge_grounded,
        )
        self.db.add(row)
        session = self.db.get(ChatSession, session_id)
        if session:
            session.last_message_at = datetime.now(timezone.utc)
        self.db.flush()
        return row

    def list_messages(self, user_id: str, session_id: str) -> list[ChatMessage]:
        return list(
            self.db.scalars(
                select(ChatMessage)
                .where(ChatMessage.user_id == user_id, ChatMessage.session_id == session_id)
                .order_by(ChatMessage.created_at.asc())
            )
        )

    def get_message(self, user_id: str, message_id: str) -> ChatMessage | None:
        return self.db.scalar(select(ChatMessage).where(ChatMessage.user_id == user_id, ChatMessage.id == message_id))

    def update_message(self, message: ChatMessage, **values) -> ChatMessage:
        for key, value in values.items():
            setattr(message, key, value)
        self.db.flush()
        return message

    def add_citation(self, citation: ChatMessageCitation) -> ChatMessageCitation:
        self.db.add(citation)
        self.db.flush()
        return citation

    def list_citations(self, message_id: str) -> list[ChatMessageCitation]:
        return list(
            self.db.scalars(
                select(ChatMessageCitation)
                .where(ChatMessageCitation.message_id == message_id)
                .order_by(ChatMessageCitation.citation_index.asc())
            )
        )

    def list_citations_for_messages(self, message_ids: list[str]) -> list[ChatMessageCitation]:
        if not message_ids:
            return []
        return list(
            self.db.scalars(
                select(ChatMessageCitation)
                .where(ChatMessageCitation.message_id.in_(message_ids))
                .order_by(ChatMessageCitation.message_id.asc(), ChatMessageCitation.citation_index.asc())
            )
        )
