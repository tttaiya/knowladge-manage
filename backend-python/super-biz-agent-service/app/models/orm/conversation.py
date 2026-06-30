"""Conversation, message, and citation ORM models."""

from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Index, Integer, JSON, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base
from app.models.orm.common import TimestampMixin, new_id


class ChatSession(TimestampMixin, Base):
    __tablename__ = "chat_sessions"
    __table_args__ = (Index("ix_chat_sessions_user_id", "user_id"),)

    id: Mapped[str] = mapped_column(String(64), primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    title: Mapped[str] = mapped_column(String(200), default="新会话", nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="active", nullable=False)
    last_message_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    summary: Mapped[str | None] = mapped_column(Text, nullable=True)
    summary_updated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class ChatMessage(TimestampMixin, Base):
    __tablename__ = "chat_messages"
    __table_args__ = (
        Index("ix_chat_messages_session_id", "session_id"),
        Index("ix_chat_messages_user_id", "user_id"),
    )

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    session_id: Mapped[str] = mapped_column(String(64), ForeignKey("chat_sessions.id"), nullable=False)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False)
    role: Mapped[str] = mapped_column(String(20), nullable=False)
    content: Mapped[str] = mapped_column(Text, default="", nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="completed", nullable=False)
    intent: Mapped[str | None] = mapped_column(String(40), nullable=True)
    is_knowledge_grounded: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    error_message: Mapped[str | None] = mapped_column(Text, nullable=True)
    token_usage: Mapped[dict | None] = mapped_column(JSON, nullable=True)
    latency_ms: Mapped[int | None] = mapped_column(Integer, nullable=True)


class ChatMessageCitation(TimestampMixin, Base):
    __tablename__ = "chat_message_citations"
    __table_args__ = (Index("ix_chat_message_citations_message_id", "message_id"),)

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    message_id: Mapped[str] = mapped_column(String(36), ForeignKey("chat_messages.id"), nullable=False)
    citation_index: Mapped[int] = mapped_column(Integer, nullable=False)
    knowledge_base_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    knowledge_base_name: Mapped[str | None] = mapped_column(String(200), nullable=True)
    document_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    chunk_id: Mapped[str | None] = mapped_column(String(36), nullable=True)
    source_title: Mapped[str] = mapped_column(String(300), default="", nullable=False)
    section_path: Mapped[str | None] = mapped_column(String(500), nullable=True)
    content_preview: Mapped[str] = mapped_column(Text, default="", nullable=False)
    vector_score: Mapped[float | None] = mapped_column(nullable=True)
    rerank_score: Mapped[float | None] = mapped_column(nullable=True)
