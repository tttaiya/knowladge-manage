"""Conversation schemas."""

from datetime import datetime

from pydantic import BaseModel


class ConversationCreateRequest(BaseModel):
    title: str | None = None


class ConversationUpdateRequest(BaseModel):
    title: str


class ConversationResponse(BaseModel):
    id: str
    title: str
    status: str
    last_message_at: datetime | None
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class MessageResponse(BaseModel):
    id: str
    session_id: str
    role: str
    content: str
    status: str
    intent: str | None = None
    is_knowledge_grounded: bool
    created_at: datetime

    model_config = {"from_attributes": True}
