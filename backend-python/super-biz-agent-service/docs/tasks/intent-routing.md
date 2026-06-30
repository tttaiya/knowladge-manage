# intent-routing：意图识别与业务路由

目标：显式识别知识问答、通用闲聊和普通问答，替代完全依赖 Agent 自主选工具的隐式路由。

## 最小任务

- [ ] 新增 `app/services/intent_service.py`
- [ ] 定义意图枚举 `knowledge_qa`、`general_chat`、`normal_qa`
- [ ] 定义意图识别结果模型，包含 `intent`、`confidence`、`reason`
- [ ] 实现关键词规则识别器
- [ ] 实现 LLM 分类器接口
- [ ] 实现低置信度兜底策略
- [ ] 新增配置项 `intent.confidence_threshold`
- [ ] 支持请求 `mode=knowledge` 强制知识问答
- [ ] 支持请求 `mode=chat` 强制普通对话
- [ ] 在 SSE 中返回 `intent_detection` 节点状态
- [ ] 在 `chat_messages.intent` 中保存意图结果
- [ ] 新增测试：闲聊问题输出 `general_chat`
- [ ] 新增测试：电力规范问题输出 `knowledge_qa`
- [ ] 新增测试：强制知识库模式覆盖自动识别
- [ ] 新增测试：低置信度按配置兜底
- [ ] 新增测试：识别结果可序列化给前端

## 完成标准

- [ ] 闲聊问题不触发知识库检索
- [ ] 电力规范类问题进入 RAG Pipeline
- [ ] 前端可展示本轮意图和识别原因

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增规则优先的 `IntentService`，支持 `knowledge_qa`、`general_chat`、`normal_qa` 和 `mode` 强制覆盖；聊天响应和 SSE 事件返回意图信息。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：LLM 分类器接口通过 LLM Factory 预留，当前默认规则识别。
