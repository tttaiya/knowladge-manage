"""Bad case 数据模型。"""

from typing import Literal

from pydantic import BaseModel, Field


CaseType = Literal["retrieval", "tool", "prompt", "model", "workflow", "system"]


class BadCaseCreateRequest(BaseModel):
    trace_id: str
    case_type: CaseType
    symptom: str
    root_cause: str
    fix_action: str
    verification_result: str = ""


class BadCaseRecord(BaseModel):
    case_id: str
    trace_id: str
    case_type: CaseType
    symptom: str
    root_cause: str
    fix_action: str
    verification_result: str = ""
    created_at: str
    updated_at: str
    tags: list[str] = Field(default_factory=list)
