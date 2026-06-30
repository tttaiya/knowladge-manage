"""请求数据模型

定义 API 请求的 Pydantic 模型
"""

from pydantic import BaseModel, Field, model_validator


class ChatRequest(BaseModel):
    """对话请求"""

    id: str | None = Field(None, description="会话 ID", alias="Id")
    question: str | None = Field(None, description="用户问题", alias="Question")
    session_id: str | None = None
    knowledge_base_ids: list[str] | None = None
    mode: str = "auto"

    @model_validator(mode="after")
    def normalize_fields(self):
        if self.session_id and not self.id:
            self.id = self.session_id
        if not self.question:
            raise ValueError("question is required")
        return self

    class Config:
        populate_by_name = True
        json_schema_extra = {
            "example": {
                "Id": "session-123",
                "Question": "什么是向量数据库？"
            }
        }


class ClearRequest(BaseModel):
    """清空会话请求"""

    session_id: str = Field(..., description="会话 ID", alias="sessionId")

    class Config:
        populate_by_name = True
