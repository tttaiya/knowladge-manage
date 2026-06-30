"""统一工具执行器。"""

import asyncio
import json
import time
import uuid
from pathlib import Path
from typing import Any, Awaitable, Callable

from loguru import logger

from app.agent.tool_registry import ToolResult, tool_registry
from app.models.trace import ToolCallRecord, now_iso
from app.services.context_compressor import context_compressor
from app.services.trace_service import TraceService, trace_service as default_trace_service


class ToolExecutor:
    """统一执行工具并写入 trace。"""

    def __init__(
        self,
        trace_service: TraceService = default_trace_service,
        raw_output_dir: str = "traces/raw_outputs",
    ) -> None:
        self.trace_service = trace_service
        self.raw_output_dir = Path(raw_output_dir)
        self.raw_output_dir.mkdir(parents=True, exist_ok=True)

    async def execute(
        self,
        *,
        trace,
        step_index: int,
        tool_name: str,
        input_args: dict[str, Any],
        call: Callable[..., Awaitable[Any]] | Callable[..., Any],
    ) -> ToolResult:
        meta = tool_registry.get(tool_name)
        if meta is None:
            return self._failure(tool_name, "UNKNOWN_ERROR", f"工具未注册: {tool_name}")

        if not meta.enabled:
            return self._failure(tool_name, "PERMISSION_DENIED", f"工具已禁用: {tool_name}")

        started = time.perf_counter()
        tool_call_id = f"tool_{uuid.uuid4().hex[:10]}"

        try:
            result = await asyncio.wait_for(
                self._call_tool(call, input_args),
                timeout=meta.timeout_ms / 1000,
            )
            latency_ms = int((time.perf_counter() - started) * 1000)
            raw_output_ref = self._save_raw_output(trace.trace_id, tool_call_id, result)
            summary = self._summarize_result(result)

            tool_result = ToolResult(
                tool_name=tool_name,
                success=True,
                data=result,
                summary=summary,
                latency_ms=latency_ms,
                raw_output_ref=raw_output_ref,
            )
            self.trace_service.record_tool_call(
                trace,
                ToolCallRecord(
                    tool_call_id=tool_call_id,
                    trace_id=trace.trace_id,
                    step_index=step_index,
                    tool_name=tool_name,
                    input_args=input_args,
                    output_summary=summary,
                    raw_output_ref=raw_output_ref,
                    latency_ms=latency_ms,
                    status="success",
                    created_at=now_iso(),
                ),
            )
            return tool_result

        except asyncio.TimeoutError:
            latency_ms = int((time.perf_counter() - started) * 1000)
            message = f"工具调用超时: {tool_name}"
            self._record_failed_call(
                trace=trace,
                tool_call_id=tool_call_id,
                step_index=step_index,
                tool_name=tool_name,
                input_args=input_args,
                latency_ms=latency_ms,
                status="timeout",
                error_type="TIMEOUT",
                error_message=message,
            )
            return self._failure(tool_name, "TIMEOUT", message, latency_ms)

        except Exception as exc:
            latency_ms = int((time.perf_counter() - started) * 1000)
            error_type = self._classify_error(exc)
            message = str(exc)
            logger.warning(f"工具调用失败: {tool_name}, {error_type}, {message}")
            self._record_failed_call(
                trace=trace,
                tool_call_id=tool_call_id,
                step_index=step_index,
                tool_name=tool_name,
                input_args=input_args,
                latency_ms=latency_ms,
                status="failed",
                error_type=error_type,
                error_message=message,
            )
            return self._failure(tool_name, error_type, message, latency_ms)

    async def _call_tool(self, call, input_args: dict[str, Any]) -> Any:
        result = call(**input_args)
        if asyncio.iscoroutine(result):
            return await result
        return result

    def _save_raw_output(self, trace_id: str, tool_call_id: str, result: Any) -> str:
        trace_dir = self.raw_output_dir / trace_id
        trace_dir.mkdir(parents=True, exist_ok=True)
        path = trace_dir / f"{tool_call_id}.json"
        path.write_text(
            json.dumps(result, ensure_ascii=False, indent=2, default=str),
            encoding="utf-8",
        )
        return str(path)

    def _summarize_result(self, result: Any) -> str:
        compressed = context_compressor.compress(result)
        summary = compressed["summary_text"]
        if len(summary) <= 800:
            return summary
        return summary[:800] + "...[compressed]"

    def _classify_error(self, exc: Exception) -> str:
        message = str(exc).lower()
        if "permission" in message or "forbidden" in message:
            return "PERMISSION_DENIED"
        if "argument" in message or "validation" in message:
            return "INVALID_ARGUMENT"
        if "empty" in message or "not found" in message:
            return "EMPTY_RESULT"
        if "remote" in message or "http" in message or "connection" in message:
            return "REMOTE_ERROR"
        return "UNKNOWN_ERROR"

    def _record_failed_call(
        self,
        *,
        trace,
        tool_call_id: str,
        step_index: int,
        tool_name: str,
        input_args: dict[str, Any],
        latency_ms: int,
        status: str,
        error_type: str,
        error_message: str,
    ) -> None:
        self.trace_service.record_tool_call(
            trace,
            ToolCallRecord(
                tool_call_id=tool_call_id,
                trace_id=trace.trace_id,
                step_index=step_index,
                tool_name=tool_name,
                input_args=input_args,
                latency_ms=latency_ms,
                status=status,  # type: ignore[arg-type]
                error_type=error_type,
                error_message=error_message,
                created_at=now_iso(),
            ),
        )

    def _failure(
        self,
        tool_name: str,
        error_type: str,
        error_message: str,
        latency_ms: int = 0,
    ) -> ToolResult:
        return ToolResult(
            tool_name=tool_name,
            success=False,
            error_type=error_type,  # type: ignore[arg-type]
            error_message=error_message,
            latency_ms=latency_ms,
        )


tool_executor = ToolExecutor()
