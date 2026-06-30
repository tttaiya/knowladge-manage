# 工具清单

| 工具名 | 类型 | 风险等级 | 超时 | 重试 | 说明 |
|---|---|---|---|---|---|
| `retrieve_knowledge` | 本地工具 | `read_only` | 15000ms | 0 | 查询运维知识库 |
| `get_current_time` | 本地工具 | `read_only` | 3000ms | 0 | 获取当前时间 |
| `query_prometheus_alerts` | 本地工具 | `read_only` | 10000ms | 1 | 查询 Prometheus 告警 |
| `mcp_cls_query` | MCP 工具 | `read_only` | 15000ms | 1 | 查询日志 |
| `mcp_monitor_query` | MCP 工具 | `read_only` | 15000ms | 1 | 查询监控 |
