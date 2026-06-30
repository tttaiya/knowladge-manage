"""Admin dashboard API."""

from datetime import date

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.db.session import get_db
from app.models.orm.user import User
from app.services.dashboard_service import DashboardService

router = APIRouter(prefix="/admin/dashboard")


@router.get("/summary")
def summary(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return DashboardService(db).summary()


@router.get("/qa-trend")
def trend(start: date | None = None, end: date | None = None, user: User = Depends(require_login), db: Session = Depends(get_db)):
    return DashboardService(db).trend(start, end)


@router.get("/recent-questions")
def recent_questions(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return {"items": DashboardService(db).recent_questions()}
