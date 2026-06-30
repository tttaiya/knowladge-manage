"""Admin settings API."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.core.llm_factory import llm_factory
from app.db.session import get_db
from app.models.orm.user import User
from app.models.schemas.settings import SettingResponse, SettingUpdateRequest
from app.services.settings_service import SettingsService

router = APIRouter(prefix="/admin/settings")


@router.get("", response_model=list[SettingResponse])
def list_settings(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return SettingsService(db).list_settings()


@router.patch("/{key:path}", response_model=SettingResponse)
def update_setting(key: str, request: SettingUpdateRequest, user: User = Depends(require_login), db: Session = Depends(get_db)):
    return SettingsService(db).update(key, request.value, user.id)


@router.post("/reset", response_model=list[SettingResponse])
def reset_settings(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return SettingsService(db).reset()


@router.post("/llm/test")
def test_llm(user: User = Depends(require_login), db: Session = Depends(get_db)):
    return {"code": 200, "message": "success", "data": {"success": True, "provider": llm_factory.get_config(db).provider}}
