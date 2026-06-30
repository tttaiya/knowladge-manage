"""User repository."""

from datetime import datetime, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.auth.tokens import hash_refresh_token
from app.models.orm.user import RefreshToken, User


class UserRepository:
    def __init__(self, db: Session):
        self.db = db

    def get_by_username(self, username: str) -> User | None:
        return self.db.scalar(select(User).where(User.username == username))

    def get_by_id(self, user_id: str) -> User | None:
        return self.db.get(User, user_id)

    def create(self, username: str, password_hash: str, display_name: str | None = None) -> User:
        user = User(username=username, password_hash=password_hash, display_name=display_name or username)
        self.db.add(user)
        self.db.flush()
        return user

    def update_last_login(self, user: User) -> None:
        user.last_login_at = datetime.now(timezone.utc)
        self.db.flush()

    def create_refresh_token(self, user_id: str, token: str, expires_at: datetime) -> RefreshToken:
        row = RefreshToken(user_id=user_id, token_hash=hash_refresh_token(token), expires_at=expires_at)
        self.db.add(row)
        self.db.flush()
        return row

    def get_refresh_token(self, token: str) -> RefreshToken | None:
        return self.db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hash_refresh_token(token)))

    def revoke_refresh_token(self, row: RefreshToken) -> None:
        row.revoked_at = datetime.now(timezone.utc)
        self.db.flush()
