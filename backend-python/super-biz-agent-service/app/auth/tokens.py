"""JWT access tokens and opaque refresh tokens."""

from datetime import datetime, timedelta, timezone
import hashlib
import secrets

import jwt

from app.config import config

ALGORITHM = "HS256"


def create_access_token(user_id: str, username: str, expires_minutes: int | None = None) -> str:
    now = datetime.now(timezone.utc)
    expires = now + timedelta(minutes=expires_minutes or config.access_token_expire_minutes)
    payload = {"sub": user_id, "username": username, "iat": int(now.timestamp()), "exp": int(expires.timestamp())}
    return jwt.encode(payload, config.auth_secret_key, algorithm=ALGORITHM)


def decode_access_token(token: str) -> dict:
    return jwt.decode(token, config.auth_secret_key, algorithms=[ALGORITHM])


def generate_refresh_token() -> str:
    return secrets.token_urlsafe(48)


def hash_refresh_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()
