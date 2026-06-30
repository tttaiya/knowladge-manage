"""QA metric ORM model."""

from sqlalchemy import Boolean, ForeignKey, Integer, JSON, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base
from app.models.orm.common import TimestampMixin, new_id


class QAMetric(TimestampMixin, Base):
    __tablename__ = "qa_metrics"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=new_id)
    user_id: Mapped[str] = mapped_column(String(36), ForeignKey("users.id"), nullable=False, index=True)
    session_id: Mapped[str] = mapped_column(String(64), nullable=False, index=True)
    message_id: Mapped[str | None] = mapped_column(String(36), nullable=True, index=True)
    question: Mapped[str] = mapped_column(Text, nullable=False)
    intent: Mapped[str | None] = mapped_column(String(40), nullable=True)
    hit_knowledge: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    knowledge_base_ids: Mapped[list | None] = mapped_column(JSON, nullable=True)
    citation_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    latency_ms: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    status: Mapped[str] = mapped_column(String(20), default="success", nullable=False)
    error_type: Mapped[str | None] = mapped_column(String(80), nullable=True)
