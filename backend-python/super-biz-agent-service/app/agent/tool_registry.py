"""工具注册中心。

这里保存工具治理需要的元信息，不直接负责执行工具。
"""

from typing import Any, Literal

from pydantic import BaseModel, Field


RiskLevel = Literal["read_only", "low_risk_write", "high_risk_write"]
ToolErrorType = Literal[
    "TIMEOUT",
    "INVALID_ARGUMENT",
    "PERMISSION_DENIED",
    "EMPTY_RESULT",
    "REMOTE_ERROR",
    "UNKNOWN_ERROR",
]


class ToolMeta(BaseModel):
    """工具治理元信息。"""

    name: str
    description: str
    timeout_ms: int = 10_000
    retry_count: int = 0
    risk_level: RiskLevel = "read_only"
    enabled: bool = True
    owner: str = "system"
    input_schema: dict[str, Any] = Field(default_factory=dict)
    output_schema: dict[str, Any] = Field(default_factory=dict)


class ToolResult(BaseModel):
    """统一工具执行结果。"""

    tool_name: str
    success: bool
    data: Any = None
    summary: str | None = None
    error_type: ToolErrorType | None = None
    error_message: str | None = None
    latency_ms: int = 0
    raw_output_ref: str | None = None


class ToolRegistry:
    """内存工具注册表。"""

    def __init__(self) -> None:
        self._tools: dict[str, ToolMeta] = {}

    def register(self, meta: ToolMeta) -> None:
        self._tools[meta.name] = meta

    def get(self, name: str) -> ToolMeta | None:
        return self._tools.get(name)

    def list_tools(self, include_disabled: bool = False) -> list[ToolMeta]:
        tools = list(self._tools.values())
        if include_disabled:
            return tools
        return [tool for tool in tools if tool.enabled]

    def disable(self, name: str) -> None:
        if name in self._tools:
            self._tools[name].enabled = False

    def enable(self, name: str) -> None:
        if name in self._tools:
            self._tools[name].enabled = True


tool_registry = ToolRegistry()


def register_default_tools() -> None:
    """注册当前项目已有工具的治理元信息。"""
    tool_registry.register(
        ToolMeta(
            name="retrieve_knowledge",
            description="从本地知识库中检索运维经验文档",
            timeout_ms=15_000,
            retry_count=0,
            risk_level="read_only",
            owner="rag",
        )
    )
    tool_registry.register(
        ToolMeta(
            name="get_current_time",
            description="获取当前时间",
            timeout_ms=3_000,
            retry_count=0,
            risk_level="read_only",
            owner="system",
        )
    )
    tool_registry.register(
        ToolMeta(
            name="query_prometheus_alerts",
            description="查询 Prometheus 当前活跃告警",
            timeout_ms=10_000,
            retry_count=1,
            risk_level="read_only",
            owner="monitor",
        )
    )
    tool_registry.register(
        ToolMeta(
            name="mcp_cls_query",
            description="通过 MCP 查询日志服务",
            timeout_ms=15_000,
            retry_count=1,
            risk_level="read_only",
            owner="mcp",
        )
    )
    tool_registry.register(
        ToolMeta(
            name="mcp_monitor_query",
            description="通过 MCP 查询监控指标",
            timeout_ms=15_000,
            retry_count=1,
            risk_level="read_only",
            owner="mcp",
        )
    )


register_default_tools()
