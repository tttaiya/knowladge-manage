"""Dashboard schemas."""

from pydantic import BaseModel


class DashboardSummary(BaseModel):
    user_count: int
    knowledge_base_count: int
    document_count: int
    chunk_count: int
    qa_count: int
