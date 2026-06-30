# Tool Registry 与 ToolExecutor 设计

## 背景

当前 Executor 主要依赖 LangGraph `ToolNode` 自动执行工具。这个方式使用方便，但不利于统一处理超时、重试、风险等级、错误分类和调用审计。

## 目标

- 统一维护工具元信息。
- 支持工具启用和禁用。
- 支持工具风险等级。
- 支持工具执行结果标准化。
- 支持工具调用写入 trace。

## 风险等级

- `read_only`: 只读查询工具。
- `low_risk_write`: 低风险写操作。
- `high_risk_write`: 高风险写操作，后续需要人工确认。

## 错误类型

- `TIMEOUT`
- `INVALID_ARGUMENT`
- `PERMISSION_DENIED`
- `EMPTY_RESULT`
- `REMOTE_ERROR`
- `UNKNOWN_ERROR`

## 当前落地范围

Day 04 已落地 `ToolRegistry` 和 `ToolResult`。
Day 05 已落地 `ToolExecutor`，支持成功、超时、异常、未注册工具的标准化返回，并将工具调用写入 trace。

当前 Executor 仍保留 `ToolNode` 主链路，同时把 LLM 工具调用意图写入步骤结果。后续可以进一步让所有真实工具调用完全经过 `ToolExecutor`。
