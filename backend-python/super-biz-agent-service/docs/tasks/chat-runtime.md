# chat-runtime：用户问答主链路、SSE、停止与重新生成

目标：重构问答入口，使其串联会话、意图识别、RAG/普通对话、SSE 事件、消息落库和统计写入。

## 最小任务

- [ ] 新增统一问答请求模型，支持 `session_id`、`question`、`knowledge_base_ids`、`mode`
- [ ] 新增统一 SSE 事件构造函数
- [ ] 在问答开始时自动创建会话或校验已有会话归属
- [ ] 在问答开始时保存用户消息
- [ ] 在问答开始时创建助手消息并标记 `streaming`
- [ ] 定义 `session_created` SSE 事件
- [ ] 定义 `message_created` SSE 事件
- [ ] 定义 `node_status` SSE 事件
- [ ] 定义 `content` SSE 事件
- [ ] 定义 `done` SSE 事件
- [ ] 定义 `error` SSE 事件
- [ ] 定义 `stopped` SSE 事件
- [ ] 接入意图识别服务，根据结果选择 RAG Pipeline 或普通 LLM Pipeline
- [ ] 普通对话链路跳过知识库检索
- [ ] 知识问答链路调用 RAG Pipeline
- [ ] 问答完成时更新助手消息内容和状态
- [ ] 问答失败时更新助手消息状态为 `failed` 并保存错误信息
- [ ] 实现 `POST /api/chat/{message_id}/stop`
- [ ] 前端中断或后端停止时将助手消息标记为 `stopped`
- [ ] 实现 `POST /api/chat/regenerate`
- [ ] 重新生成时复用指定用户消息并创建新的助手消息
- [ ] 保留旧答案，不覆盖历史助手消息
- [ ] 新增测试：无 `session_id` 时自动创建会话
- [ ] 新增测试：问答成功后用户消息和助手消息均落库
- [ ] 新增测试：普通对话不会调用 RAG Pipeline
- [ ] 新增测试：知识问答会调用 RAG Pipeline
- [ ] 新增测试：停止生成后消息状态为 `stopped`
- [ ] 新增测试：重新生成不覆盖旧助手消息

## 完成标准

- [ ] 单次问答过程有完整 SSE 事件流
- [ ] 所有问答结果可在服务端历史中恢复
- [ ] 停止和重新生成具有明确消息状态

## 完成记录

- 完成时间：2026-06-28
- 主要改动：`/api/chat` 与 `/api/chat_stream` 接入认证、会话创建、用户/助手消息落库、意图识别、RAG/普通问答路由、引用保存和统计写入；新增停止和重新生成接口。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：普通问答使用轻量直答占位，知识问答通过可 Mock RAG Pipeline 返回结构化引用和无资料策略。
