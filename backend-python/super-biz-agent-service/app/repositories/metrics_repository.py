"""Metrics repository."""

from datetime import date, datetime, time, timedelta, timezone

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument
from app.models.orm.metrics import QAMetric
from app.models.orm.user import User


class MetricsRepository:
    def __init__(self, db: Session):
        self.db = db

    def record(self, **values) -> QAMetric:
        row = QAMetric(**values)
        self.db.add(row)
        self.db.flush()
        return row

    def summary(self) -> dict:
        return {
            "user_count": self.db.scalar(select(func.count(User.id))) or 0,
            "knowledge_base_count": self.db.scalar(
                select(func.count(KnowledgeBase.id)).where(KnowledgeBase.deleted_at.is_(None), KnowledgeBase.status != "deleted")
            )
            or 0,
            "document_count": self.db.scalar(
                select(func.count(KnowledgeDocument.id)).where(KnowledgeDocument.deleted_at.is_(None), KnowledgeDocument.status != "deleted")
            )
            or 0,
            "chunk_count": self.db.scalar(select(func.count(KnowledgeChunk.id)).where(KnowledgeChunk.deleted_at.is_(None))) or 0,
            "qa_count": self.db.scalar(select(func.count(QAMetric.id))) or 0,
        }

    def trend(self, start: date, end: date) -> list[dict]:
        items = []
        day = start
        while day <= end:
            start_dt = datetime.combine(day, time.min, tzinfo=timezone.utc)
            end_dt = start_dt + timedelta(days=1)
            count = self.db.scalar(select(func.count(QAMetric.id)).where(QAMetric.created_at >= start_dt, QAMetric.created_at < end_dt)) or 0
            users = self.db.scalar(select(func.count(func.distinct(QAMetric.user_id))).where(QAMetric.created_at >= start_dt, QAMetric.created_at < end_dt)) or 0
            items.append({"date": day.isoformat(), "qa_count": count, "active_user_count": users})
            day += timedelta(days=1)
        return items

    def recent_questions(self, limit: int = 20) -> list[QAMetric]:
        return list(self.db.scalars(select(QAMetric).order_by(QAMetric.created_at.desc()).limit(limit)))
