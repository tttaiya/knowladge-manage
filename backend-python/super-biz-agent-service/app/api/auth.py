"""Authentication API."""

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.db.session import get_db
from app.models.orm.user import User
from app.models.schemas.auth import (
    LoginRequest,
    LogoutRequest,
    RefreshRequest,
    RegisterRequest,
    TokenResponse,
    UserResponse,
)
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth")


@router.post("/register", response_model=UserResponse)
def register(request: RegisterRequest, db: Session = Depends(get_db)):
    return AuthService(db).register(request.username, request.password, request.display_name)


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    return AuthService(db).login(request.username, request.password)


@router.post("/refresh", response_model=TokenResponse)
def refresh(request: RefreshRequest, db: Session = Depends(get_db)):
    return AuthService(db).refresh(request.refresh_token)


@router.post("/logout")
def logout(request: LogoutRequest, db: Session = Depends(get_db)):
    AuthService(db).logout(request.refresh_token)
    return {"code": 200, "message": "success", "data": None}


@router.get("/me", response_model=UserResponse)
def me(user: User = Depends(require_login)):
    return user
