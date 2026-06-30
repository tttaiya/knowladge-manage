import json

from app.models.trace import TraceRecord
from app.services.trace_service import TraceService


def test_trace_service_creates_and_persists_trace(tmp_path):
    service = TraceService(base_dir=str(tmp_path))

    trace = service.create_trace(session_id="s1", user_input="diagnose cpu")

    assert trace.trace_id.startswith("trace_")
    assert trace.session_id == "s1"
    assert trace.user_input == "diagnose cpu"
    assert trace.status == "running"

    trace_path = service.get_trace_path(trace.trace_id, trace.started_at)
    assert trace_path.exists()

    saved = json.loads(trace_path.read_text(encoding="utf-8"))
    assert saved["trace_id"] == trace.trace_id
    assert saved["session_id"] == "s1"
    assert saved["status"] == "running"


def test_trace_service_finishes_and_fails_trace(tmp_path):
    service = TraceService(base_dir=str(tmp_path))
    trace = service.create_trace(session_id="s2", user_input="diagnose memory")

    finished = service.finish_trace(trace, "final report")

    assert finished.status == "success"
    assert finished.final_report == "final report"
    assert finished.ended_at is not None

    failed = service.fail_trace(
        TraceRecord(
            trace_id="trace_manual",
            session_id="s3",
            user_input="diagnose disk",
            started_at=finished.started_at,
        ),
        "boom",
    )

    assert failed.status == "failed"
    assert failed.error_message == "boom"
    assert failed.ended_at is not None


def test_trace_service_records_node_and_tool_call(tmp_path):
    from app.models.trace import ToolCallRecord, now_iso

    service = TraceService(base_dir=str(tmp_path))
    trace = service.create_trace(session_id="s4", user_input="diagnose node")

    service.record_node(
        trace=trace,
        node_name="planner",
        input_snapshot={"input": "diagnose node"},
        output_snapshot={"plan": ["step 1"]},
    )
    service.record_tool_call(
        trace,
        ToolCallRecord(
            tool_call_id="tool_1",
            trace_id=trace.trace_id,
            step_index=1,
            tool_name="retrieve_knowledge",
            input_args={"query": "cpu"},
            output_summary="cpu doc",
            latency_ms=12,
            status="success",
            created_at=now_iso(),
        ),
    )

    saved = json.loads(service.get_trace_path(trace.trace_id, trace.started_at).read_text(encoding="utf-8"))
    assert saved["node_records"][0]["node_name"] == "planner"
    assert saved["node_records"][0]["output_snapshot"]["plan"] == ["step 1"]
    assert saved["tool_calls"][0]["tool_name"] == "retrieve_knowledge"
    assert saved["tool_calls"][0]["status"] == "success"

