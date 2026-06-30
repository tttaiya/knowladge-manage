"""Agent run trace 数据模型。"""

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, Field


TraceStatus = Literal["running", "success", "failed"]
NodeName = Literal["planner", "executor", "replanner", "system"]
ToolStatus = Literal["success", "failed", "timeout", "skipped"]


def now_iso() -> str:
    """返回本地 ISO 时间字符串，精确到秒。"""
    return datetime.now().isoformat(timespec="seconds")


class NodeRecord(BaseModel):
    """单个 LangGraph 节点的输入输出快照。"""

    node_name: NodeName
    input_snapshot: dict[str, Any] = Field(default_factory=dict)
    output_snapshot: dict[str, Any] = Field(default_factory=dict)
    started_at: str
    ended_at: str | None = None
    error_message: str | None = None


class ToolCallRecord(BaseModel):
    """单次工具调用记录。"""

    tool_call_id: str
    trace_id: str
    step_index: int
    tool_name: str
    input_args: dict[str, Any] = Field(default_factory=dict)
    output_summary: str | None = None
    raw_output_ref: str | None = None
    latency_ms: int | None = None
    status: ToolStatus
    error_type: str | None = None
    error_message: str | None = None
    created_at: str


class TraceRecord(BaseModel):
    """一次 Agent 执行的完整 trace。"""

    trace_id: str
    session_id: str
    user_input: str
    status: TraceStatus = "running"
    started_at: str
    ended_at: str | None = None
    planner_output: list[str] = Field(default_factory=list)
    executor_steps: list[dict[str, Any]] = Field(default_factory=list)
    replanner_decisions: list[dict[str, Any]] = Field(default_factory=list)
    tool_calls: list[ToolCallRecord] = Field(default_factory=list)
    node_records: list[NodeRecord] = Field(default_factory=list)
    final_report: str | None = None
    error_message: str | None = None
