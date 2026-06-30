# conversation：服务端会话与消息管理

目标：将当前前端本地会话升级为服务端持久化会话，并保证用户数据隔离。

## 最小任务

- [ ] 新增 `app/repositories/conversation_repository.py`
- [ ] 新增 `app/services/conversation_service.py`
- [ ] 新增 `app/models/schemas/conversation.py`
- [ ] 新增 `app/api/conversations.py`
- [ ] 实现创建空会话服务方法
- [ ] 实现按首条用户消息生成默认标题的方法
- [ ] 实现当前用户会话列表查询，按 `last_message_at` 倒序
- [ ] 实现会话详情查询并校验归属
- [ ] 实现会话消息列表查询并校验归属
- [ ] 实现会话标题更新并校验归属
- [ ] 实现会话软删除并校验归属
- [ ] 实现保存用户消息方法
- [ ] 实现保存助手消息方法
- [ ] 实现更新消息状态方法，支持 `pending`、`streaming`、`completed`、`stopped`、`failed`
- [ ] 将旧 `/api/chat/session/{session_id}` 改为校验当前用户归属
- [ ] 将旧 `/api/chat/clear` 改为只允许删除当前用户自己的会话
- [ ] 新增测试：用户只能看到自己的会话
- [ ] 新增测试：删除会话后列表不再返回
- [ ] 新增测试：用户 A 访问用户 B 会话返回 403 或 404
- [ ] 新增测试：会话按最近消息时间倒序
- [ ] 新增测试：首条用户消息可生成截断标题

## 完成标准

- [ ] 清空浏览器缓存后，登录仍可看到历史会话
- [ ] 会话和消息以数据库为权威数据源
- [ ] 前端传入任意 `session_id` 都必须经过归属校验

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增会话仓储、服务、Schema 和 `/api/conversations` CRUD；聊天历史兼容接口改为按当前用户校验归属；问答消息落库。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：前端已从服务端加载会话列表，同时保留本地历史作为兼容缓存。
