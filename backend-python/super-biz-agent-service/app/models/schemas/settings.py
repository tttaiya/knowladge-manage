"""Settings schemas."""

from typing import Any

from pydantic import BaseModel


class SettingResponse(BaseModel):
    key: str
    value: Any
    value_type: str
    default_value: Any
    description: str
    scope: str
    is_secret: bool


class SettingUpdateRequest(BaseModel):
    value: Any
