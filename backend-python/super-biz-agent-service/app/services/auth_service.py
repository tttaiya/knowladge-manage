"""Authentication service."""

from datetime import datetime, timedelta, timezone

from fastapi import HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.auth.password import hash_password, verify_password
from app.auth.tokens import create_access_token, generate_refresh_token
from app.config import config
from app.repositories.user_repository import UserRepository


class AuthService:
    def __init__(self, db: Session):
        self.db = db
        self.users = UserRepository(db)

    def register(self, username: str, password: str, display_name: str | None = None):
        if len(password) < config.password_min_length:
            raise HTTPException(status_code=400, detail="密码长度不符合要求")
        if self.users.get_by_username(username):
            raise HTTPException(status_code=400, detail="用户名已存在")
        try:
            user = self.users.create(username, hash_password(password), display_name)
            self.db.commit()
            self.db.refresh(user)
            return user
        except IntegrityError as exc:
            self.db.rollback()
            raise HTTPException(status_code=400, detail="用户名已存在") from exc

    def login(self, username: str, password: str) -> dict:
        user = self.users.get_by_username(username)
        if not user or user.status != "active" or not verify_password(password, user.password_hash):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")
        self.users.update_last_login(user)
        token_pair = self._issue_token_pair(user.id, user.username)
        self.db.commit()
        return token_pair

    def refresh(self, refresh_token: str) -> dict:
        row = self.users.get_refresh_token(refresh_token)
        now = datetime.now(timezone.utc)
        if not row or row.revoked_at is not None or row.expires_at < now:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Refresh Token 无效")
        user = self.users.get_by_id(row.user_id)
        if not user or user.status != "active":
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户不可用")
        self.users.revoke_refresh_token(row)
        token_pair = self._issue_token_pair(user.id, user.username)
        self.db.commit()
        return token_pair

    def logout(self, refresh_token: str) -> None:
        row = self.users.get_refresh_token(refresh_token)
        if row and row.revoked_at is None:
            self.users.revoke_refresh_token(row)
            self.db.commit()

    def _issue_token_pair(self, user_id: str, username: str) -> dict:
        access_token = create_access_token(user_id, username)
        refresh_token = generate_refresh_token()
        expires_at = datetime.now(timezone.utc) + timedelta(days=config.refresh_token_expire_days)
        self.users.create_refresh_token(user_id, refresh_token, expires_at)
        return {"access_token": access_token, "refresh_token": refresh_token, "token_type": "bearer"}
