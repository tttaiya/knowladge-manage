# 总体进度

本文用于跟踪 `docs/design/proposal.md` 与 `docs/design/detailed-design.md` 拆分出的模块级任务完成情况。

## 模块完成情况

- [x] `database-foundation`：数据库基础设施与 ORM 模型
- [x] `auth-permission`：认证、Token 与登录态权限
- [x] `conversation`：服务端会话与消息管理
- [x] `chat-runtime`：用户问答主链路、SSE、停止与重新生成
- [x] `context-management`：上下文裁剪与摘要压缩
- [x] `intent-routing`：意图识别与业务路由
- [x] `rag-pipeline`：RAG 检索、阈值、重排序与无资料策略
- [x] `citation`：引用溯源与引用详情
- [x] `knowledge-base`：知识库、文档与索引管理
- [x] `system-settings`：动态系统配置
- [x] `llm-config`：大模型配置与 LLM Factory
- [x] `admin-frontend`：管理后台前端
- [x] `dashboard-metrics`：运营统计与管理总览

## 建议执行顺序

- [x] 第一阶段：完成 `database-foundation`
- [x] 第一阶段：完成 `auth-permission`
- [x] 第二阶段：完成 `conversation`
- [x] 第二阶段：完成 `chat-runtime`
- [x] 第二阶段：完成 `context-management`
- [x] 第三阶段：完成 `intent-routing`
- [x] 第三阶段：完成 `rag-pipeline`
- [x] 第三阶段：完成 `citation`
- [x] 第四阶段：完成 `knowledge-base`
- [x] 第四阶段：完成 `system-settings`
- [x] 第四阶段：完成 `llm-config`
- [x] 第五阶段：完成 `admin-frontend`
- [x] 第五阶段：完成 `dashboard-metrics`

## 集成验收

- [x] 未登录用户访问业务接口返回 401
- [x] 未登录用户访问后台接口返回 401
- [x] 已登录用户可访问后台接口
- [x] 登录后可创建、查看、删除自己的会话
- [x] 用户之间无法访问彼此会话和消息
- [x] 知识问答可返回结构化引用
- [x] 无资料时不会伪造知识库依据
- [x] 已登录用户可配置 RAG 参数且下一次请求生效
- [x] 已登录用户可管理知识库和文档索引
- [x] 总览页可展示用户数、知识库数、文档数、切片数、问答总次数和近 30 天趋势

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增 SQLAlchemy 持久化底座、JWT/Refresh Token 认证、登录态依赖、服务端会话与消息、动态配置、意图识别、可 Mock RAG Pipeline、引用持久化、知识库管理接口、Dashboard 统计接口、静态后台页面和前端登录态接入。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 静态检查：`node --check static\app.js`、`node --check static\admin.js` 均通过；`ruff check app tests` 失败，主要为既有 MCP/工具模块导入排序、旧 typing 写法和空白字符问题，本轮未做大面积无关格式化。
- 实现备注：当前版本采用单机 SQLite 默认数据库与同步知识库索引；RAG、重排序和 LLM 连通性保留可 Mock/可替换接口，真实外部 Milvus 不可用时应用启动降级不失败。

