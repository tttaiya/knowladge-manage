import asyncio


def test_default_tool_registry_contains_governance_metadata():
    from app.agent.tool_registry import tool_registry

    tools = {tool.name: tool for tool in tool_registry.list_tools()}

    assert "retrieve_knowledge" in tools
    assert "get_current_time" in tools
    assert "query_prometheus_alerts" in tools
    assert tools["retrieve_knowledge"].risk_level == "read_only"
    assert tools["query_prometheus_alerts"].retry_count == 1
    assert tools["get_current_time"].timeout_ms <= 3000


def test_tool_executor_records_successful_tool_call(tmp_path):
    from app.agent.tool_executor import ToolExecutor
    from app.services.trace_service import TraceService

    async def run():
        trace_service = TraceService(base_dir=str(tmp_path / "traces"))
        trace = trace_service.create_trace("tool-session", "call tool")
        executor = ToolExecutor(trace_service=trace_service, raw_output_dir=str(tmp_path / "raw"))

        async def fake_tool(query: str):
            return {"answer": f"doc for {query}"}

        result = await executor.execute(
            trace=trace,
            step_index=1,
            tool_name="retrieve_knowledge",
            input_args={"query": "cpu"},
            call=fake_tool,
        )
        return trace_service, trace, result

    trace_service, trace, result = asyncio.run(run())

    assert result.success is True
    assert result.tool_name == "retrieve_knowledge"
    assert result.raw_output_ref is not None
    saved = trace_service.get_trace_path(trace.trace_id, trace.started_at).read_text(encoding="utf-8")
    assert "retrieve_knowledge" in saved
    assert "doc for cpu" in saved


def test_tool_executor_classifies_unregistered_tool(tmp_path):
    from app.agent.tool_executor import ToolExecutor
    from app.services.trace_service import TraceService

    async def run():
        trace_service = TraceService(base_dir=str(tmp_path / "traces"))
        trace = trace_service.create_trace("tool-session", "bad tool")
        executor = ToolExecutor(trace_service=trace_service, raw_output_dir=str(tmp_path / "raw"))
        return await executor.execute(
            trace=trace,
            step_index=1,
            tool_name="not_registered",
            input_args={},
            call=lambda: "never called",
        )

    result = asyncio.run(run())

    assert result.success is False
    assert result.error_type == "UNKNOWN_ERROR"
    assert "未注册" in result.error_message


def test_prometheus_alert_tool_falls_back_to_demo_alerts(monkeypatch):
    from app.tools import query_metrics_alerts

    monkeypatch.setattr(
        query_metrics_alerts,
        "query_prometheus_alerts_api",
        lambda: ({}, "failed to query Prometheus alerts: 502 Bad Gateway"),
    )

    result = query_metrics_alerts.query_prometheus_alerts.invoke({})

    assert '"success": true' in result
    assert "demo_fallback" in result
    assert "CPUHighUsage" in result
