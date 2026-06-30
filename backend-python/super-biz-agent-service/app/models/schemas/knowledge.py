"""Knowledge API schemas."""

from pydantic import BaseModel


class KnowledgeBaseCreate(BaseModel):
    name: str
    code: str
    description: str | None = None


class KnowledgeBaseUpdate(BaseModel):
    name: str | None = None
    description: str | None = None
    status: str | None = None
