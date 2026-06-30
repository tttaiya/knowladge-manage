"""Dashboard metrics service."""

from datetime import date, timedelta

from sqlalchemy.orm import Session

from app.repositories.metrics_repository import MetricsRepository


class DashboardService:
    def __init__(self, db: Session):
        self.repo = MetricsRepository(db)

    def record_qa(self, **values):
        return self.repo.record(**values)

    def summary(self):
        return self.repo.summary()

    def trend(self, start: date | None = None, end: date | None = None):
        end = end or date.today()
        start = start or (end - timedelta(days=29))
        return {"date_range": [start.isoformat(), end.isoformat()], "items": self.repo.trend(start, end)}

    def recent_questions(self):
        return [
            {
                "id": row.id,
                "question": row.question,
                "intent": row.intent,
                "status": row.status,
                "created_at": row.created_at,
            }
            for row in self.repo.recent_questions()
        ]
