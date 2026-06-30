# 智能问答系统详细设计文档

## 1. 文档说明

本文基于 `docs/design/proposal.md` 编写，用于指导智能问答系统后续产品化改造。设计目标是在当前 FastAPI + LangChain/LangGraph + Milvus + 静态前端项目基础上，补齐认证授权、服务端会话、意图路由、RAG 增强、引用溯源、知识库管理、管理后台、动态配置和运营统计能力。

已确认技术决策：

- 业务数据持久化使用 `SQLite/PostgreSQL + SQLAlchemy`，开发环境可使用 SQLite，生产环境建议 PostgreSQL。
- 登录认证使用 `JWT + Refresh Token`。
- 后台前端允许按前后端分离方式设计，当前可先落在静态页面，后续可迁移到 Vue/React。
- Milvus 继续使用单 collection，通过 metadata 中的 `knowledge_base_id` 过滤不同知识库。
- 当前版本采用单一登录用户权限模型，不设置额外管理角色；所有已登录用户都可以使用管理后台能力。

设计原则：

- 模块之间保持低耦合，通过服务层和接口契约协作。
- 每个模块都应可独立测试，避免必须启动完整 LLM 或 Milvus 才能跑单元测试。
- 后端必须作为权限边界，前端隐藏按钮不作为安全控制。
- RAG 回答必须区分“知识库依据”和“非知识库依据”。
- 管理后台配置变更应尽量实时生效，减少重启。

## 2. 总体架构

### 2.1 分层架构

系统分为以下层次：

- API 层：FastAPI Router，负责请求校验、鉴权依赖、响应封装。
- Service 层：业务逻辑编排，例如认证、会话、问答、RAG、知识库、配置、统计。
- Repository 层：通过 SQLAlchemy 访问关系型数据库。
- Vector 层：负责 Milvus 向量写入、检索、删除和 metadata 过滤。
- LLM 层：封装对话模型、摘要模型、意图识别模型、重排序模型调用。
- Frontend 层：用户端问答页面和管理后台页面。

### 2.2 后端目录建议

```text
app/
  api/
    auth.py
    users.py
    chat.py
    conversations.py
    knowledge_base.py
    admin_settings.py
    admin_dashboard.py
    rag.py
  auth/
    dependencies.py
    password.py
    tokens.py
    permissions.py
  db/
    base.py
    session.py
    migrations/
  models/
    orm/
      user.py
      conversation.py
      knowledge.py
      settings.py
      metrics.py
    schemas/
      auth.py
      conversation.py
      knowledge.py
      rag.py
      settings.py
      dashboard.py
  repositories/
    user_repository.py
    conversation_repository.py
    knowledge_repository.py
    settings_repository.py
    metrics_repository.py
  services/
    auth_service.py
    conversation_service.py
    intent_service.py
    rag_pipeline_service.py
    citation_service.py
    knowledge_base_service.py
    settings_service.py
    dashboard_service.py
    context_service.py
  services/rerank/
    base.py
    dashscope_reranker.py
    mock_reranker.py
```

当前已有的 `app/services/rag_agent_service.py`、`app/tools/knowledge_tool.py`、`app/services/vector_*` 可逐步改造，不需要一次性删除。

### 2.3 核心链路

用户问答链路：

```text
用户登录
  -> 获取 access_token / refresh_token
  -> 创建或选择会话
  -> 提交问题
  -> 鉴权与会话归属校验
  -> 写入用户消息
  -> 意图识别
  -> 知识问答进入 RAG Pipeline
  -> 普通对话进入 Direct LLM Pipeline
  -> 流式返回思考节点与答案片段
  -> 写入助手消息、引用、问答统计
```

知识库管理链路：

```text
用户登录
  -> 创建知识库
  -> 上传文档
  -> 保存原文和文档元数据
  -> 文档切片
  -> 写入 Milvus，metadata 记录 knowledge_base_id/document_id/chunk_id
  -> 更新索引状态和切片数量
```

后台配置链路：

```text
用户修改配置
  -> 后端登录校验
  -> 参数合法性校验
  -> 写入 system_settings
  -> SettingsService 刷新内存缓存
  -> 下一次请求读取最新配置
```

## 3. 数据库设计

### 3.1 通用字段约定

所有业务表建议包含：

- `id`：字符串 UUID 或数据库自增 ID，建议 UUID。
- `created_at`：创建时间。
- `updated_at`：更新时间。
- `deleted_at`：软删除时间，可为空。

时间统一使用 UTC 存储，前端按用户时区展示。

### 3.2 用户与权限表

#### users

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 用户 ID |
| username | String | 用户名，唯一 |
| password_hash | String | 密码哈希 |
| display_name | String | 展示名 |
| status | String | `active`、`disabled` |
| last_login_at | DateTime | 最近登录时间 |
| created_at | DateTime | 创建时间 |
| updated_at | DateTime | 更新时间 |

#### refresh_tokens

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | Token ID |
| user_id | UUID/String | 用户 ID |
| token_hash | String | Refresh Token 哈希 |
| expires_at | DateTime | 过期时间 |
| revoked_at | DateTime | 吊销时间 |
| created_at | DateTime | 创建时间 |

Access Token 不落库，Refresh Token 落库并支持吊销。

### 3.3 会话与消息表

#### chat_sessions

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 会话 ID |
| user_id | UUID/String | 所属用户 |
| title | String | 会话标题 |
| status | String | `active`、`deleted` |
| last_message_at | DateTime | 最近消息时间 |
| summary | Text | 历史压缩摘要 |
| summary_updated_at | DateTime | 摘要更新时间 |
| created_at | DateTime | 创建时间 |
| updated_at | DateTime | 更新时间 |

#### chat_messages

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 消息 ID |
| session_id | UUID/String | 会话 ID |
| user_id | UUID/String | 用户 ID |
| role | String | `user`、`assistant`、`system` |
| content | Text | 消息内容 |
| status | String | `pending`、`streaming`、`completed`、`stopped`、`failed` |
| intent | String | 意图类型 |
| is_knowledge_grounded | Boolean | 是否基于知识库 |
| error_message | Text | 错误信息 |
| token_usage | JSON | Token 使用量 |
| latency_ms | Integer | 耗时 |
| created_at | DateTime | 创建时间 |

#### chat_message_citations

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 引用 ID |
| message_id | UUID/String | 助手消息 ID |
| citation_index | Integer | 引用序号 |
| knowledge_base_id | UUID/String | 知识库 ID |
| document_id | UUID/String | 文档 ID |
| chunk_id | UUID/String | 切片 ID |
| source_title | String | 来源文档名 |
| section_path | String | 章节路径 |
| content_preview | Text | 切片内容 |
| vector_score | Float | 向量分数 |
| rerank_score | Float | 重排序分数 |

### 3.4 知识库表

#### knowledge_bases

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 知识库 ID |
| name | String | 名称 |
| code | String | 编码，唯一 |
| description | Text | 描述 |
| status | String | `enabled`、`disabled` |
| created_by | UUID/String | 创建人 |
| created_at | DateTime | 创建时间 |
| updated_at | DateTime | 更新时间 |

#### knowledge_documents

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 文档 ID |
| knowledge_base_id | UUID/String | 所属知识库 |
| filename | String | 原始文件名 |
| file_path | String | 服务端原文路径 |
| file_ext | String | 扩展名 |
| file_size | Integer | 文件大小 |
| checksum | String | 文件校验值 |
| status | String | `uploaded`、`indexing`、`indexed`、`failed`、`deleted` |
| chunk_count | Integer | 切片数量 |
| error_message | Text | 索引失败原因 |
| uploaded_by | UUID/String | 上传人 |
| created_at | DateTime | 创建时间 |
| updated_at | DateTime | 更新时间 |

#### knowledge_chunks

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 切片 ID |
| knowledge_base_id | UUID/String | 知识库 ID |
| document_id | UUID/String | 文档 ID |
| milvus_vector_id | String | Milvus 主键 |
| section_path | String | 章节路径 |
| content | Text | 切片内容 |
| chunk_index | Integer | 切片序号 |
| token_count | Integer | 估算 Token 数 |
| created_at | DateTime | 创建时间 |

Milvus metadata 必须包含：

```json
{
  "knowledge_base_id": "kb_xxx",
  "document_id": "doc_xxx",
  "chunk_id": "chunk_xxx",
  "source": "uploads/kb_xxx/file.md",
  "file_name": "file.md",
  "section_path": "一级标题 > 二级标题",
  "status": "enabled"
}
```

### 3.5 系统配置表

#### system_settings

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 配置 ID |
| key | String | 配置键，唯一 |
| value | Text | 配置值，加密或明文 |
| value_type | String | `string`、`int`、`float`、`bool`、`json`、`secret` |
| default_value | Text | 默认值 |
| description | Text | 说明 |
| scope | String | `llm`、`rag`、`system`、`security` |
| is_secret | Boolean | 是否敏感 |
| updated_by | UUID/String | 更新人 |
| updated_at | DateTime | 更新时间 |

敏感配置只允许后端解密使用，前端展示脱敏值。

### 3.6 统计表

#### qa_metrics

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | UUID/String | 统计记录 ID |
| user_id | UUID/String | 用户 ID |
| session_id | UUID/String | 会话 ID |
| message_id | UUID/String | 助手消息 ID |
| question | Text | 用户问题 |
| intent | String | 意图 |
| hit_knowledge | Boolean | 是否命中知识库 |
| knowledge_base_ids | JSON | 参与检索的知识库 |
| citation_count | Integer | 引用数量 |
| latency_ms | Integer | 总耗时 |
| status | String | `success`、`failed`、`stopped` |
| error_type | String | 错误类型 |
| created_at | DateTime | 创建时间 |

## 4. 认证与权限模块设计

### 4.1 模块职责

认证模块负责：

- 用户注册、登录、退出。
- 密码哈希与校验。
- Access Token 签发与校验。
- Refresh Token 签发、刷新、吊销。
- 当前用户解析。
- 登录态权限校验。

该模块不直接处理问答、知识库或统计业务。

### 4.2 Token 设计

Access Token：

- JWT 格式。
- 有效期建议 15 到 30 分钟。
- Payload 包含 `sub`、`username`、`exp`、`iat`。
- 前端通过 `Authorization: Bearer <token>` 携带。

Refresh Token：

- 随机高强度字符串。
- 有效期建议 7 到 30 天。
- 后端只保存哈希值。
- 刷新时采用轮换策略：旧 Refresh Token 吊销，签发新 Refresh Token。

### 4.3 权限定义

当前版本使用单一登录用户权限模型：

- 未登录用户：只能访问静态资源、注册、登录、刷新 Token 等公开接口。
- 已登录用户：可使用智能问答、自己的会话、知识库管理、系统配置、模型配置和运营统计。
- 用户之间的数据仍需隔离：会话、消息等个人数据必须按 `user_id` 校验归属。

权限依赖：

```python
get_current_user()
require_login()
```

### 4.4 API 设计

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 公开 | 注册用户 |
| POST | `/api/auth/login` | 公开 | 登录 |
| POST | `/api/auth/refresh` | Refresh Token | 刷新 Token |
| POST | `/api/auth/logout` | 登录用户 | 退出登录 |
| GET | `/api/auth/me` | 登录用户 | 当前用户信息 |

### 4.5 独立测试点

- 密码哈希后不等于明文。
- 正确密码可登录，错误密码不可登录。
- 无 Token 访问受保护接口返回 401。
- 已登录用户可访问管理接口。
- 未登录用户访问管理接口返回 401。
- Refresh Token 刷新后旧 Token 失效。

## 5. 服务端会话模块设计

### 5.1 模块职责

会话模块负责：

- 创建会话。
- 查询当前用户会话列表。
- 查询会话消息。
- 更新会话标题。
- 删除会话。
- 校验会话归属。

会话模块不负责调用 LLM，只管理会话和消息数据。

### 5.2 会话标题策略

默认标题由首条用户消息生成：

- 去除换行和多余空格。
- 最长 30 个中文字符或 60 个英文字符。
- 超长追加 `...`。
- 用户后续可手动重命名。

### 5.3 API 设计

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/conversations` | 登录用户 | 创建空会话 |
| GET | `/api/conversations` | 登录用户 | 当前用户会话列表 |
| GET | `/api/conversations/{session_id}` | 会话所属用户 | 会话详情 |
| GET | `/api/conversations/{session_id}/messages` | 会话所属用户 | 消息列表 |
| PATCH | `/api/conversations/{session_id}` | 会话所属用户 | 修改标题 |
| DELETE | `/api/conversations/{session_id}` | 会话所属用户 | 删除会话 |

### 5.4 与现有接口兼容

当前 `/api/chat` 依赖前端传入 `Id`。改造后：

- 新接口优先使用 `session_id`。
- 兼容旧字段 `Id` 一段时间。
- 后端必须校验 `session_id` 属于当前用户。
- 如果没有传 `session_id`，可自动创建会话。

### 5.5 独立测试点

- 用户只能看到自己的会话。
- 删除会话后列表不再返回。
- 用户 A 访问用户 B 会话返回 404 或 403。
- 创建会话后默认按更新时间倒序排列。

## 6. 用户端问答模块设计

### 6.1 模块职责

问答模块负责编排一次问答请求：

- 保存用户问题。
- 调用意图识别。
- 根据意图路由到 RAG 或普通对话。
- 流式返回事件。
- 保存助手回答、引用和统计记录。
- 支持停止生成和重新生成。

### 6.2 请求模型

```json
{
  "session_id": "session_xxx",
  "question": "请解释某电力规范要求",
  "knowledge_base_ids": ["kb_1", "kb_2"],
  "mode": "auto"
}
```

字段说明：

- `session_id`：可选；为空时自动创建会话。
- `question`：必填。
- `knowledge_base_ids`：可选；为空时使用用户默认知识库范围。
- `mode`：`auto`、`knowledge`、`chat`，默认 `auto`。

### 6.3 SSE 事件设计

统一事件结构：

```json
{
  "type": "node_status",
  "request_id": "req_xxx",
  "session_id": "session_xxx",
  "message_id": "msg_xxx",
  "data": {}
}
```

事件类型：

- `session_created`：后端自动创建会话。
- `message_created`：用户消息已保存。
- `node_status`：思考过程节点状态变化。
- `retrieval_result`：召回结果摘要。
- `citation_created`：引用列表生成。
- `content`：答案片段。
- `done`：完成。
- `stopped`：用户停止。
- `error`：失败。

### 6.4 节点状态模型

```json
{
  "node": "intent_detection",
  "label": "意图识别",
  "status": "running",
  "message": "正在识别问题类型"
}
```

状态枚举：

- `pending`
- `running`
- `success`
- `failed`
- `skipped`

知识问答链路节点：

- `intent_detection`
- `term_matching`
- `knowledge_retrieval`
- `rerank`
- `answer_generation`
- `citation_binding`

普通对话链路节点：

- `intent_detection`
- `llm_call`
- `answer_generation`

### 6.5 停止生成设计

前端：

- 每次流式请求创建 `AbortController`。
- 点击停止时调用 `controller.abort()`。
- 同时调用后端停止接口，标记消息状态。

后端：

- 提供 `POST /api/chat/{message_id}/stop`。
- 如果底层 LLM 调用无法真正中断，也必须停止继续向前端发送数据。
- 将助手消息状态改为 `stopped`。

### 6.6 重新生成设计

重新生成入口基于上一条用户消息：

```json
{
  "session_id": "session_xxx",
  "user_message_id": "msg_user_xxx"
}
```

后端行为：

- 校验用户消息属于当前用户。
- 新建一条助手消息。
- 使用该用户消息重新执行问答链路。
- 不删除旧助手消息，前端可展示多个版本。

### 6.7 独立测试点

- 普通对话不会调用 RAG Pipeline。
- 知识问答会调用 RAG Pipeline。
- SSE 事件顺序符合预期。
- 停止生成后消息状态为 `stopped`。
- 重新生成不会覆盖原回答。

## 7. 上下文管理模块设计

### 7.1 模块职责

上下文模块负责：

- 读取会话历史消息。
- 计算当前模型上下文预算。
- 保留最近问答轮次。
- 对较早消息生成摘要。
- 将摘要写回 `chat_sessions.summary`。

### 7.2 上下文组装策略

输入：

- 当前用户问题。
- 会话历史消息。
- 会话历史摘要。
- 模型上下文窗口配置。
- 单次回复预留 Token 数。

输出：

- `system_prompt`
- `conversation_summary`
- `recent_messages`
- `current_question`

策略：

- 最近 N 轮问答原文优先保留。
- 超出预算的旧消息进入摘要。
- 摘要失败时降级为固定轮次截断，并记录日志。

### 7.3 配置项

- `context.max_recent_rounds`
- `context.summary_trigger_message_count`
- `context.max_context_tokens`
- `context.reserved_output_tokens`
- `context.summary_model`

### 7.4 独立测试点

- 消息少于阈值时不压缩。
- 消息超过阈值时生成摘要。
- 摘要写入会话表。
- 摘要失败不影响问答主流程。

## 8. 意图识别与路由模块设计

### 8.1 模块职责

意图模块负责判断问题类型，并输出可解释的路由结果。

意图枚举：

- `knowledge_qa`：知识库问答。
- `general_chat`：通用闲聊。
- `normal_qa`：普通大模型问答。

### 8.2 识别策略

第一阶段使用规则召回：

- 命中电力、规范、标准、制度、技术监督、SOP 等关键词，提高知识问答倾向。
- 命中问候、闲聊、开放创作类表达，提高普通对话倾向。

第二阶段使用轻量 LLM 分类：

```json
{
  "intent": "knowledge_qa",
  "confidence": 0.87,
  "reason": "问题询问电力规范条款，需要知识库依据"
}
```

低置信度策略：

- 低于 `intent.confidence_threshold` 时进入 `normal_qa`。
- 如果用户显式选择知识库模式，则强制进入 `knowledge_qa`。

### 8.3 独立测试点

- 闲聊问题输出 `general_chat`。
- 电力规范问题输出 `knowledge_qa`。
- 强制知识库模式覆盖自动识别。
- 低置信度按配置兜底。

## 9. RAG Pipeline 模块设计

### 9.1 模块职责

RAG Pipeline 负责：

- 根据问题和知识库范围执行向量检索。
- 根据阈值过滤低质量召回。
- 可选执行重排序。
- 处理无资料策略。
- 构造带引用候选的上下文。
- 调用 LLM 生成答案。
- 绑定结构化引用。

该模块不负责用户鉴权，也不直接操作前端。

### 9.2 Pipeline 步骤

```text
validate_kb_scope
  -> vector_retrieve
  -> vector_threshold_filter
  -> optional_rerank
  -> rerank_threshold_filter
  -> no_evidence_policy
  -> build_grounded_prompt
  -> generate_answer
  -> bind_citations
```

### 9.3 检索配置

配置项：

- `rag.retrieval_mode`：`vector`、`vector_rerank`
- `rag.vector_top_k`
- `rag.vector_score_threshold`
- `rag.rerank_top_n`
- `rag.rerank_score_threshold`
- `rag.allow_fallback_answer`
- `rag.default_knowledge_base_ids`

### 9.4 Milvus 检索过滤

使用单 collection，检索表达式示例：

```text
metadata["knowledge_base_id"] in ["kb_1", "kb_2"] and metadata["status"] == "enabled"
```

如果 Milvus JSON 字段过滤能力受版本限制，则在召回后进行二次过滤，但必须保证 `top_k` 扩大以避免过滤后结果过少。

### 9.5 分数处理

当前向量检索使用 L2 距离时，距离越小越相似。为统一前端展示，Service 层输出：

- `raw_score`：原始距离或相似度。
- `score_type`：`l2_distance`、`cosine_similarity`。
- `normalized_score`：归一化后 0 到 1，越大越相关。

阈值过滤使用 `normalized_score`。

### 9.6 重排序设计

定义抽象接口：

```python
class BaseReranker:
    async def rerank(self, query: str, chunks: list[RetrievedChunk]) -> list[RerankResult]:
        ...
```

实现：

- `DashScopeReranker`：真实模型调用。
- `MockReranker`：测试用，按输入顺序或指定分数返回。

重排序失败策略：

- 记录错误。
- 如果 `rag.rerank_failure_policy=degrade`，降级使用向量结果。
- 如果配置为 `fail`，本次问答返回错误。

### 9.7 无资料策略

无有效切片时：

- 默认返回“当前知识库未检索到足够依据”。
- 如果允许兜底回答，则调用普通 LLM 生成答案。
- 兜底回答必须设置 `is_knowledge_grounded=false`。
- 前端展示“非知识库依据”标识。

### 9.8 独立测试点

- 指定知识库 ID 后只返回该知识库切片。
- 低于阈值的切片被过滤。
- 开启重排序后结果顺序变化。
- 重排序异常时按配置降级。
- 无资料时不会生成伪引用。

## 10. 引用溯源模块设计

### 10.1 模块职责

引用模块负责：

- 将检索切片转换为引用候选。
- 为答案生成提供引用编号。
- 解析或绑定模型回答中的引用标记。
- 去重同一切片引用。
- 持久化引用信息。

### 10.2 引用候选结构

```json
{
  "citation_index": 1,
  "knowledge_base_id": "kb_1",
  "knowledge_base_name": "技术监督业务库",
  "document_id": "doc_1",
  "document_name": "规范.md",
  "chunk_id": "chunk_1",
  "section_path": "总则 > 适用范围",
  "content": "切片正文",
  "vector_score": 0.82,
  "rerank_score": 0.91
}
```

### 10.3 Prompt 约束

答案生成 Prompt 需明确：

- 只能使用给定资料回答知识库问题。
- 使用资料时在句末标注 `[1]`、`[2]`。
- 不确定时说明资料不足。
- 不得编造引用编号。

### 10.4 前端展示

前端收到：

```json
{
  "answer": "根据规范要求... [1]",
  "citations": [
    {
      "index": 1,
      "document_name": "规范.md",
      "section_path": "总则",
      "content_preview": "...",
      "vector_score": 0.82,
      "rerank_score": 0.91,
      "download_url": "/api/knowledge/documents/doc_1/download"
    }
  ]
}
```

前端能力：

- 将 `[1]` 渲染为可点击上角标或内联标记。
- 点击后打开引用详情弹窗或侧边抽屉。
- 支持下载原文。

### 10.5 独立测试点

- 同一 chunk 多次引用只生成一个编号。
- 引用编号与候选排序一致。
- 引用详情包含知识库、文档、章节、切片、分数。
- 无资料回答不返回 citations。

## 11. 知识库管理模块设计

### 11.1 模块职责

知识库模块负责：

- 知识库 CRUD。
- 文档上传、查看、删除、下载。
- 文档切片与索引。
- 重新索引。
- 启用和停用知识库。

不负责具体问答生成。

### 11.2 API 设计

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/admin/knowledge-bases` | 登录用户 | 创建知识库 |
| GET | `/api/admin/knowledge-bases` | 登录用户 | 知识库列表 |
| PATCH | `/api/admin/knowledge-bases/{kb_id}` | 登录用户 | 更新知识库 |
| DELETE | `/api/admin/knowledge-bases/{kb_id}` | 登录用户 | 删除知识库 |
| POST | `/api/admin/knowledge-bases/{kb_id}/documents` | 登录用户 | 上传文档 |
| GET | `/api/admin/knowledge-bases/{kb_id}/documents` | 登录用户 | 文档列表 |
| POST | `/api/admin/documents/{doc_id}/reindex` | 登录用户 | 重新索引 |
| DELETE | `/api/admin/documents/{doc_id}` | 登录用户 | 删除文档 |
| GET | `/api/knowledge/documents/{doc_id}/download` | 有权限用户 | 下载原文 |

### 11.3 索引流程

同步版本：

- 上传后立即执行切片和向量写入。
- 适合小文件和当前项目简化实现。

异步版本：

- 上传后状态为 `uploaded`。
- 后台任务执行索引。
- 前端轮询索引状态。

详细设计建议先支持同步索引，接口预留异步状态字段。

### 11.4 删除流程

删除文档：

- 将 `knowledge_documents.status` 标记为 `deleted`。
- 删除 Milvus 中 `metadata["document_id"] == doc_id` 的向量。
- 将 `knowledge_chunks` 软删除或物理删除。

停用知识库：

- 更新 `knowledge_bases.status=disabled`。
- 检索时过滤 disabled 知识库。
- 不必立即删除向量。

### 11.5 独立测试点

- 上传文档后生成文档记录和切片记录。
- Milvus metadata 包含 `knowledge_base_id`。
- 停用知识库后检索不到该知识库内容。
- 删除文档后引用下载接口不可访问或返回 404。

## 12. 系统配置模块设计

### 12.1 模块职责

配置模块负责：

- 管理系统参数。
- 管理 RAG 参数。
- 管理 LLM 参数。
- 参数合法性校验。
- 敏感参数脱敏展示。
- 修改后刷新运行时配置。

### 12.2 配置读取优先级

```text
数据库 system_settings
  -> .env / Settings 默认值
  -> 代码内置默认值
```

启动时：

- 从 `.env` 初始化基础配置。
- 读取数据库配置覆盖运行时配置。

运行时：

- Service 层通过 `SettingsService.get(key)` 读取配置。
- 高频配置可使用内存缓存。
- 更新配置后清理或刷新缓存。

### 12.3 API 设计

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/admin/settings` | 登录用户 | 配置列表 |
| PATCH | `/api/admin/settings/{key}` | 登录用户 | 修改配置 |
| POST | `/api/admin/settings/reset` | 登录用户 | 重置默认配置 |
| POST | `/api/admin/settings/llm/test` | 登录用户 | LLM 连通性测试 |

### 12.4 配置项初始集合

RAG：

- `rag.retrieval_mode`
- `rag.vector_top_k`
- `rag.vector_score_threshold`
- `rag.rerank_top_n`
- `rag.rerank_score_threshold`
- `rag.allow_fallback_answer`

LLM：

- `llm.provider`
- `llm.base_url`
- `llm.api_key`
- `llm.chat_model`
- `llm.summary_model`
- `llm.intent_model`
- `llm.timeout_seconds`

安全：

- `security.access_token_expire_minutes`
- `security.refresh_token_expire_days`
- `security.password_min_length`

### 12.5 独立测试点

- 已登录用户可修改配置。
- 未登录用户不能修改配置。
- 敏感配置返回脱敏值。
- 修改 Top K 后下一次 RAG 检索生效。
- 重置默认值后配置恢复。

## 13. 大模型配置模块设计

### 13.1 模块职责

大模型模块负责：

- 按用途创建 LLM Client。
- 支持不同模型用途：对话、摘要、意图识别、重排序。
- 支持配置变更后重建 Client。
- 提供连通性测试。

### 13.2 LLM Factory

接口：

```python
class LLMFactory:
    def get_chat_model(self): ...
    def get_summary_model(self): ...
    def get_intent_model(self): ...
    def get_rerank_client(self): ...
    async def test_connection(self, config: LLMConfig): ...
```

实现要求：

- 不在业务模块中直接读取 `.env`。
- 统一从 `SettingsService` 读取运行时配置。
- API Key 不写日志。

### 13.3 独立测试点

- 切换模型名称后 Factory 使用新配置。
- API Key 不出现在接口响应和日志中。
- 连通性测试失败返回可读错误。

## 14. 管理后台设计

### 14.1 前端形态

当前阶段可在 `static/admin.html`、`static/admin.js` 实现后台页面；设计上保持前后端分离接口，后续可迁移到 Vue/React。

建议前端模块：

- 登录页。
- 用户端问答页。
- 后台布局页。
- 系统设置页。
- 模型配置页。
- RAG 参数页。
- 知识库管理页。
- 文档管理页。
- 运营总览页。

### 14.2 路由建议

```text
/login
/chat
/admin
/admin/settings
/admin/models
/admin/rag
/admin/knowledge-bases
/admin/dashboard
```

静态项目可先使用 hash 路由：

```text
/#/login
/#/chat
/#/admin/settings
```

### 14.3 登录态控制

前端：

- 根据 `/api/auth/me` 判断登录态并展示后台菜单。
- Access Token 过期后调用刷新接口。
- 刷新失败跳转登录。

后端：

- 所有 `/api/admin/*` 必须校验登录态。
- 前端路由控制只作为体验优化。

### 14.4 独立测试点

- 未登录用户不展示后台菜单，直接请求后台接口返回 401。
- 已登录用户展示后台菜单。
- 已登录用户可进入后台页面并读取配置。

## 15. 运营统计模块设计

### 15.1 模块职责

统计模块负责：

- 记录每次问答指标。
- 聚合用户数、知识库数、文档数、切片数、问答总次数。
- 生成近 30 天日活趋势。
- 支持日期范围筛选。

### 15.2 指标写入时机

问答结束时写入 `qa_metrics`：

- 成功：记录 intent、hit_knowledge、citation_count、latency_ms。
- 失败：记录 status、error_type、error_message。
- 停止：记录 status=`stopped`。

### 15.3 API 设计

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/admin/dashboard/summary` | 登录用户 | 核心指标 |
| GET | `/api/admin/dashboard/qa-trend` | 登录用户 | 问答趋势 |
| GET | `/api/admin/dashboard/recent-questions` | 登录用户 | 最近问题 |

核心指标响应：

```json
{
  "user_count": 12,
  "knowledge_base_count": 3,
  "document_count": 28,
  "chunk_count": 530,
  "qa_count": 1024
}
```

趋势响应：

```json
{
  "date_range": ["2026-06-01", "2026-06-30"],
  "items": [
    {"date": "2026-06-01", "qa_count": 42, "active_user_count": 7}
  ]
}
```

### 15.4 独立测试点

- 成功问答写入一条统计记录。
- 失败问答也写入统计记录。
- 近 30 天趋势按日期补零。
- 未登录用户不能访问统计接口。
- 已登录用户可访问统计接口。

## 16. 前端用户端改造设计

### 16.1 登录态管理

前端保存：

- `access_token`：可保存在内存或 `sessionStorage`。
- `refresh_token`：可保存在 `localStorage` 或更安全的 HttpOnly Cookie。当前设计采用前端持有 Token，后续可增强为 HttpOnly Cookie。

请求拦截：

- 每次 API 请求带 `Authorization`。
- 401 时尝试刷新 Token。
- 刷新失败跳转登录。

### 16.2 聊天输入

将 `input` 改为 `textarea`：

- `Enter` 发送。
- `Shift+Enter` 换行。
- 根据内容自动增高，设置最大高度。

### 16.3 会话列表

由服务端会话接口驱动：

- 页面加载时调用 `/api/conversations`。
- 新建会话调用 `POST /api/conversations`。
- 删除会话调用 `DELETE /api/conversations/{id}`。
- 本地 `localStorage` 只作为草稿缓存，不作为权威数据源。

### 16.4 思考过程面板

组件状态：

```json
[
  {"node": "intent_detection", "status": "success", "message": "识别为知识问答"},
  {"node": "knowledge_retrieval", "status": "running", "message": "正在检索知识库"}
]
```

交互：

- 生成中默认展开。
- 完成后自动折叠。
- 用户可手动展开查看。

### 16.5 引用展示

前端处理：

- 将回答中的 `[1]` 渲染为可点击元素。
- 点击打开引用详情。
- 支持原文下载。

### 16.6 独立测试点

- 未登录访问聊天页跳转登录。
- Token 过期后可刷新。
- 会话列表来自服务端。
- 停止按钮能停止流式读取。
- 引用点击能展示详情。

## 17. 接口响应规范

### 17.1 普通 JSON 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

错误响应：

```json
{
  "code": 401,
  "message": "unauthorized",
  "data": null,
  "error": {
    "type": "AUTH_REQUIRED",
    "detail": "请先登录"
  }
}
```

### 17.2 错误类型

- `AUTH_REQUIRED`
- `PERMISSION_DENIED`
- `VALIDATION_ERROR`
- `RESOURCE_NOT_FOUND`
- `LLM_CALL_FAILED`
- `RAG_RETRIEVAL_FAILED`
- `RERANK_FAILED`
- `INDEX_FAILED`

### 17.3 SSE 错误事件

```json
{
  "type": "error",
  "request_id": "req_xxx",
  "data": {
    "error_type": "LLM_CALL_FAILED",
    "message": "模型调用失败，请稍后重试",
    "retryable": true
  }
}
```

## 18. 测试设计

### 18.1 单元测试

每个模块应有独立单元测试：

- `auth_service`：密码、Token、登录态校验。
- `conversation_service`：会话归属、列表排序、删除。
- `intent_service`：规则和低置信度兜底。
- `rag_pipeline_service`：过滤、重排序、无资料策略。
- `citation_service`：引用编号和去重。
- `settings_service`：配置读取、脱敏、刷新。
- `dashboard_service`：指标聚合和日期补零。

### 18.2 集成测试

使用 SQLite 测试数据库：

- 注册登录后访问问答接口。
- 未登录用户访问后台接口返回 401。
- 已登录用户访问后台接口成功。
- 上传文档到知识库后可检索。
- 问答完成后写入消息和统计。

LLM 和 Milvus 可通过 Mock 替代，避免测试依赖外部服务。

### 18.3 API 测试

使用 FastAPI TestClient 或 httpx：

- `/api/auth/*`
- `/api/conversations/*`
- `/api/chat_stream`
- `/api/admin/settings`
- `/api/admin/knowledge-bases`
- `/api/admin/dashboard/*`

### 18.4 前端测试建议

当前静态前端可通过手工检查和浏览器自动化验证：

- 登录跳转。
- 会话切换。
- 流式输出。
- 停止生成。
- 引用详情。
- 后台菜单登录态控制。

后续迁移 Vue/React 后再补充组件测试。

## 19. 迭代计划

### 19.1 第一阶段：安全底座

交付内容：

- SQLAlchemy 数据库基础设施。
- 用户、Refresh Token 表。
- 注册、登录、刷新、退出接口。
- 鉴权依赖和登录态权限控制。
- 受保护接口统一接入鉴权。

可独立验收：

- 未登录不能访问业务接口。
- 未登录不能访问后台接口。
- 已登录用户可以访问后台接口。

### 19.2 第二阶段：服务端会话与问答链路

交付内容：

- 服务端会话和消息表。
- 会话 CRUD。
- 问答接口绑定用户和会话。
- 停止生成、重新生成。
- 上下文压缩服务。

可独立验收：

- 换浏览器登录后仍能看到历史会话。
- 用户之间会话隔离。

### 19.3 第三阶段：RAG 增强与引用溯源

交付内容：

- 意图识别与路由。
- RAG Pipeline。
- 多知识库过滤。
- 阈值过滤。
- 重排序接口。
- 无资料策略。
- 结构化引用。

可独立验收：

- 闲聊不触发知识库。
- 知识问答返回引用。
- 无资料不伪造来源。

### 19.4 第四阶段：知识库管理与后台配置

交付内容：

- 知识库 CRUD。
- 文档上传、删除、重新索引、下载。
- 系统配置页。
- 模型配置页。
- RAG 参数页。

可独立验收：

- 已登录用户可配置 RAG 参数并实时生效。
- 停用知识库后不参与检索。

### 19.5 第五阶段：运营统计与后台体验

交付内容：

- 问答统计写入。
- 管理总览指标。
- 近 30 天趋势。
- 后台页面完善。

可独立验收：

- 总览指标正确。
- 趋势图按日期展示问答量和活跃用户。

## 20. 风险与处理

### 20.1 JWT 存储风险

风险：前端持有 Token 时存在 XSS 泄露风险。

处理：

- 当前阶段先使用 Bearer Token。
- 后续可切换 Refresh Token 到 HttpOnly Cookie。
- 前端 Markdown 渲染必须做 XSS 防护。

### 20.2 Milvus JSON 过滤兼容性

风险：不同 Milvus 版本对 JSON metadata 过滤能力可能不同。

处理：

- 优先使用 metadata 过滤。
- 如果不可用，扩大召回数量后在应用层过滤。
- 保留 `knowledge_chunks` 表作为检索结果校验来源。

### 20.3 重排序模型不稳定

风险：重排序接口超时或失败会影响问答。

处理：

- 设置超时。
- 默认失败降级到向量检索。
- 记录 `RERANK_FAILED` 指标。

### 20.4 动态配置一致性

风险：多进程部署时，单进程内存缓存可能不一致。

处理：

- 单机阶段更新后刷新本进程缓存。
- 多进程阶段增加配置版本号或引入 Redis Pub/Sub。

### 20.5 会话上下文与 LangGraph MemorySaver 重复

风险：当前 MemorySaver 只适合进程内会话，不适合生产持久化。

处理：

- 以数据库 `chat_messages` 作为权威历史。
- LangGraph MemorySaver 可保留为运行时缓存。
- 每次问答前由 `ContextService` 从数据库组装上下文。

## 21. 非本期设计范围

以下能力不纳入本详细设计主线：

- MCP 工具治理增强。
- 企业单点登录 SSO。
- 多租户组织架构。
- 复杂审计合规报表。
- 分布式任务队列和对象存储生产化改造。

