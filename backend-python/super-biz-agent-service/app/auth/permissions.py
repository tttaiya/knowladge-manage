"""Permission dependencies."""

from fastapi import Depends

from app.auth.dependencies import get_current_user
from app.models.orm.user import User


def require_login(user: User = Depends(get_current_user)) -> User:
    return user
