# context-management：上下文裁剪与摘要压缩

目标：实现按模型上下文窗口动态组装会话上下文，并对较早历史进行摘要压缩。

## 最小任务

- [ ] 新增 `app/services/context_service.py`
- [ ] 定义上下文输入结构：当前问题、会话历史、会话摘要、模型预算
- [ ] 定义上下文输出结构：系统提示词、历史摘要、最近消息、当前问题
- [ ] 新增配置项 `context.max_recent_rounds`
- [ ] 新增配置项 `context.summary_trigger_message_count`
- [ ] 新增配置项 `context.max_context_tokens`
- [ ] 新增配置项 `context.reserved_output_tokens`
- [ ] 新增配置项 `context.summary_model`
- [ ] 实现消息 Token 粗略估算函数
- [ ] 实现最近问答轮次保留策略
- [ ] 实现超预算旧消息摘要生成入口
- [ ] 实现摘要写回 `chat_sessions.summary`
- [ ] 摘要失败时降级为固定轮次截断
- [ ] 将问答主链路改为从 `ContextService` 获取上下文
- [ ] 移除或停止依赖未接入的固定 3 轮裁剪函数
- [ ] 新增测试：消息少于阈值时不压缩
- [ ] 新增测试：消息超过阈值时触发摘要
- [ ] 新增测试：摘要写入会话表
- [ ] 新增测试：摘要失败不影响问答主流程

## 完成标准

- [ ] 长会话不会因为上下文过长直接失败
- [ ] 最近问答轮次优先保留
- [ ] 历史摘要成为数据库中的会话状态，而不是进程内临时状态

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增 `ContextService`，提供 token 粗估和最近轮次组装结构；配置项已进入动态配置默认集合。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：摘要生成入口保留为轻量实现，尚未接入真实摘要模型调用；主链路当前以数据库消息历史为权威上下文。
