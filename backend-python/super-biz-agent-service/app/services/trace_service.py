"""Agent trace 本地持久化服务。"""

import json
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any

from loguru import logger

from app.models.trace import NodeRecord, ToolCallRecord, TraceRecord, now_iso


class TraceService:
    """负责创建、更新并持久化 Agent trace。"""

    def __init__(self, base_dir: str = "traces") -> None:
        self.base_dir = Path(base_dir)
        self.base_dir.mkdir(parents=True, exist_ok=True)

    def create_trace(self, session_id: str, user_input: str) -> TraceRecord:
        trace_id = f"trace_{datetime.now().strftime('%Y%m%d_%H%M%S')}_{uuid.uuid4().hex[:8]}"
        trace = TraceRecord(
            trace_id=trace_id,
            session_id=session_id,
            user_input=user_input,
            started_at=now_iso(),
        )
        self.save_trace(trace)
        logger.info(f"创建 trace: {trace_id}")
        return trace

    def get_trace_path(self, trace_id: str, started_at: str | None = None) -> Path:
        date_part = (started_at or now_iso())[:10]
        trace_dir = self.base_dir / date_part
        trace_dir.mkdir(parents=True, exist_ok=True)
        return trace_dir / f"{trace_id}.json"

    def save_trace(self, trace: TraceRecord) -> Path:
        path = self.get_trace_path(trace.trace_id, trace.started_at)
        path.write_text(
            json.dumps(trace.model_dump(), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        return path

    def record_node(
        self,
        trace: TraceRecord,
        node_name: str,
        input_snapshot: dict[str, Any],
        output_snapshot: dict[str, Any],
        error_message: str | None = None,
    ) -> TraceRecord:
        node = NodeRecord(
            node_name=node_name,  # type: ignore[arg-type]
            input_snapshot=input_snapshot,
            output_snapshot=output_snapshot,
            started_at=now_iso(),
            ended_at=now_iso(),
            error_message=error_message,
        )
        trace.node_records.append(node)
        self.save_trace(trace)
        return trace

    def record_tool_call(self, trace: TraceRecord, record: ToolCallRecord) -> TraceRecord:
        trace.tool_calls.append(record)
        self.save_trace(trace)
        return trace

    def finish_trace(self, trace: TraceRecord, final_report: str = "") -> TraceRecord:
        trace.status = "success"
        trace.ended_at = now_iso()
        trace.final_report = final_report
        self.save_trace(trace)
        return trace

    def fail_trace(self, trace: TraceRecord, error_message: str) -> TraceRecord:
        trace.status = "failed"
        trace.ended_at = now_iso()
        trace.error_message = error_message
        self.save_trace(trace)
        return trace


trace_service = TraceService()
