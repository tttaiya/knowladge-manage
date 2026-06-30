# llm-config：大模型配置与 LLM Factory

目标：统一管理对话模型、摘要模型、意图识别模型和重排序模型配置，使模型配置可后台修改并实时用于后续请求。

## 最小任务

- [ ] 扩展 `app/core/llm_factory.py`，改为从 `SettingsService` 获取运行时配置
- [ ] 定义 `LLMConfig` 数据结构
- [ ] 实现 `get_chat_model`
- [ ] 实现 `get_summary_model`
- [ ] 实现 `get_intent_model`
- [ ] 实现 `get_rerank_client`
- [ ] 支持配置 `llm.provider`
- [ ] 支持配置 `llm.base_url`
- [ ] 支持配置 `llm.api_key`
- [ ] 支持配置 `llm.chat_model`
- [ ] 支持配置 `llm.summary_model`
- [ ] 支持配置 `llm.intent_model`
- [ ] 支持配置 `llm.timeout_seconds`
- [ ] 实现 LLM Client 缓存
- [ ] 配置变化后清理对应 LLM Client 缓存
- [ ] 实现 `POST /api/admin/settings/llm/test`
- [ ] 连通性测试成功时返回模型基础信息或简单成功状态
- [ ] 连通性测试失败时返回可读失败原因
- [ ] 确保 API Key 不出现在接口响应中
- [ ] 确保 API Key 不出现在日志中
- [ ] 修改现有 RAG Agent、ContextService、IntentService 使用 LLM Factory
- [ ] 新增测试：切换模型名称后 Factory 使用新配置
- [ ] 新增测试：API Key 不出现在响应中
- [ ] 新增测试：连通性测试失败返回可读错误
- [ ] 新增测试：配置变化后 Client 缓存失效

## 完成标准

- [ ] 模型配置通过后台修改后下一次请求生效
- [ ] 不同用途模型可以独立配置
- [ ] 业务模块不再直接读取 `.env` 中的模型配置

## 完成记录

- 完成时间：2026-06-28
- 主要改动：扩展 `LLMFactory`，新增 `LLMConfig`、`get_chat_model`、`get_summary_model`、`get_intent_model`、`get_rerank_client` 和连通性测试接口；后台配置可覆盖模型参数。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：连通性测试为无外部调用的安全检查，避免测试依赖真实 API Key。
