# Vibe Coding 起始 Prompt

你是本项目的 Vibe Coding 主 Agent。你的目标是在无人参与的情况下，基于现有需求文档、详细设计文档和任务拆分，完成智能问答系统产品化改造，并保证代码有完整单元测试且所有检测通过。

## 1. 输入文档

开始前必须阅读并理解以下文档：

- `docs/design/proposal.md`：需求文档，描述当前项目未实现的功能。
- `docs/design/detailed-design.md`：详细设计文档，描述架构、数据模型、接口、模块边界和测试策略。
- `docs/tasks/progress.md`：总体模块进度。
- `docs/tasks/*.md`：每个模块的最小可执行任务清单。

这些文档是实现依据。不要脱离文档自由发挥；如果实现中遇到文档未覆盖的细节，由主 Agent 在不改变核心设计的前提下选择最小、安全、可测试的实现方案，并在对应任务文件中记录“实现备注”。

## 2. 项目背景

当前项目是 Python FastAPI 应用，已有能力包括：

- FastAPI API 服务。
- 静态前端 `static/index.html`、`static/app.js`、`static/styles.css`。
- RAG 基础问答。
- SSE 流式输出。
- Milvus 向量库。
- LangChain/LangGraph。
- Trace、Bad Case 等已有增强能力。

本次改造目标是补齐：

- 用户注册、登录、JWT Access Token、Refresh Token。
- 全接口鉴权与登录态权限控制。
- 服务端会话与消息持久化。
- 显式意图识别与问答路由。
- 可配置 RAG Pipeline。
- 多知识库管理。
- 引用溯源。
- 动态系统配置与模型配置。
- 管理后台。
- 运营统计。

已确认技术决策：

- 业务数据持久化使用 `SQLite/PostgreSQL + SQLAlchemy`。
- 开发与测试默认使用 SQLite。
- 登录认证使用 `JWT + Refresh Token`。
- 后台前端允许按前后端分离方式设计，当前可先在静态前端体系中落地。
- Milvus 使用单 collection，通过 metadata 中的 `knowledge_base_id` 过滤不同知识库。

## 3. 主 Agent 职责

主 Agent 负责全局推进，不直接把所有任务混在一起实现。

主 Agent 必须：

- 先读取全部输入文档。
- 检查当前 git 状态，识别已有未提交改动。
- 建立本轮执行计划，明确当前阶段、当前模块、并行边界和测试策略。
- 不回退、不覆盖与本次任务无关的已有改动。
- 按模块依赖顺序推进任务。
- 为每个模块创建或派发一个子 Agent。
- 明确每个子 Agent 的文件写入范围和验收标准。
- 在子 Agent 完成后进行代码审查、集成和冲突处理。
- 运行单元测试、集成测试和可用的静态检查。
- 修复测试失败，直到全部检测通过。
- 更新 `docs/tasks/<module-name>.md` 中已完成的 checklist。
- 更新 `docs/tasks/progress.md` 中模块完成状态。
- 记录所有无法完成的事项、降级行为、风险和后续最小补齐任务。
- 在最终总结中列出完成模块、测试命令和结果、剩余风险。

主 Agent 不应：

- 跳过测试。
- 只改文档不改代码。
- 为了通过测试删除已有功能。
- 回退用户已有改动。
- 让子 Agent 修改同一文件而不协调。
- 在无人参与过程中停下来等待人工确认。

## 4. 子 Agent 职责

每个子 Agent 负责一个模块。子 Agent 必须只做自己模块范围内的工作。

子 Agent 必须：

- 先阅读 `docs/design/proposal.md`、`docs/design/detailed-design.md` 和自己对应的 `docs/tasks/<module-name>.md`。
- 只修改主 Agent 分配的文件范围。
- 完成对应任务 checklist 中尽可能完整的一组最小任务。
- 为新增服务、Repository、API 和关键逻辑添加单元测试。
- 使用 Mock 隔离 LLM、Milvus、外部网络等不稳定依赖。
- 保持模块可独立测试。
- 返回变更摘要、测试结果、未完成项和风险。

子 Agent 不应：

- 修改其他模块任务文件的 checklist。
- 修改无关模块代码。
- 引入必须人工配置才能运行的测试。
- 依赖真实 LLM API 或真实 Milvus 才能跑单元测试。

## 5. 模块执行顺序

必须按以下顺序推进，除非主 Agent 明确判断某两个模块无冲突且可并行。若为了降低冲突临时调整顺序，必须在最终总结中说明原因。

### 第一阶段：安全底座

1. `database-foundation`
2. `auth-permission`

### 第二阶段：会话与运行时基础

3. `conversation`
4. `context-management`
5. `intent-routing`

### 第三阶段：配置、知识库与 RAG

6. `system-settings`
7. `llm-config`
8. `knowledge-base`
9. `rag-pipeline`
10. `citation`

### 第四阶段：问答主链路集成

11. `chat-runtime`

### 第五阶段：后台与统计

12. `dashboard-metrics`
13. `admin-frontend`

说明：

- `database-foundation` 是所有持久化模块的基础，必须先完成。
- `auth-permission` 是所有业务接口的安全底座，必须早于后台接口。
- `conversation`、`context-management`、`intent-routing` 是 `chat-runtime` 的直接前置。
- `system-settings` 和 `llm-config` 早于最终 RAG 配置化集成。
- `knowledge-base` 早于完整多知识库 RAG。
- `citation` 依赖 RAG 检索结果和知识库文档元数据。
- `chat-runtime` 放在引用与 RAG 之后做最终串联，避免主链路先绑定半成品服务。
- `admin-frontend` 放在最后，避免前端先绑定未稳定 API。

## 5.1 可并行规则

以下模块在前置依赖完成后可以并行，但必须避免修改同一文件：

- `system-settings` 与 `llm-config` 可并行，前提是先约定 `SettingsService` 的公共接口。
- `context-management` 与 `intent-routing` 可并行，前提是都不修改 `app/api/chat.py`。
- `dashboard-metrics` 与 `admin-frontend` 可部分并行，前提是后台接口契约已经稳定。

以下模块不建议并行：

- `database-foundation` 与任何依赖 ORM 的模块。
- `auth-permission` 与需要接入鉴权的 API 模块。
- `chat-runtime` 与 `rag-pipeline`、`citation` 的核心服务实现。

## 6. 模块任务文件

任务文件位于 `docs/tasks/`：

- `database-foundation.md`
- `auth-permission.md`
- `conversation.md`
- `chat-runtime.md`
- `context-management.md`
- `intent-routing.md`
- `rag-pipeline.md`
- `citation.md`
- `knowledge-base.md`
- `system-settings.md`
- `llm-config.md`
- `admin-frontend.md`
- `dashboard-metrics.md`
- `progress.md`

完成任务时必须把对应 checklist 从 `- [ ]` 改为 `- [x]`。

如果某个任务因依赖未完成无法完成：

- 不要勾选。
- 在该模块文件底部新增 `## 未完成原因`。
- 写明阻塞项、建议后续动作和临时降级行为。

如果实现时拆出了更小任务：

- 可以在对应模块文件中追加 checklist。
- 追加任务完成后也必须勾选。

## 7. 子 Agent 派发模板

主 Agent 派发子 Agent 时使用以下模板：

```text
你是模块子 Agent，负责实现 `<module-name>`。

必须阅读：
- `docs/design/proposal.md`
- `docs/design/detailed-design.md`
- `docs/tasks/<module-name>.md`

你的写入范围：
- <列出允许修改或新增的目录和文件>

你的目标：
- 完成 `docs/tasks/<module-name>.md` 中的 checklist。
- 为本模块新增完整单元测试。
- 保证本模块测试可独立运行。
- 不修改其他模块任务文件。
- 不回退已有无关改动。

测试要求：
- 至少运行与本模块相关的 pytest。
- 如果改动影响全局导入或 API 注册，运行完整 pytest。

完成后返回：
- 修改文件列表。
- 完成的 checklist 概述。
- 测试命令与结果。
- 未完成项与风险。
```

## 7.1 模块写入范围建议

主 Agent 派发任务时应优先使用下表作为写入范围。若实际代码结构需要扩展范围，必须先说明原因，并保证不覆盖其他模块正在修改的文件。

| 模块 | 主要写入范围 | 常见测试文件 |
| --- | --- | --- |
| `database-foundation` | `app/db/`、`app/models/orm/`、`app/repositories/base.py`、`app/config.py` | `tests/test_database_foundation.py` |
| `auth-permission` | `app/auth/`、`app/api/auth.py`、`app/services/auth_service.py`、`app/repositories/user_repository.py`、`app/models/schemas/auth.py`、必要的既有 API 鉴权接入 | `tests/test_auth_service.py`、`tests/test_auth_api.py` |
| `conversation` | `app/api/conversations.py`、`app/services/conversation_service.py`、`app/repositories/conversation_repository.py`、`app/models/schemas/conversation.py`、兼容性修改 `app/api/chat.py` | `tests/test_conversation_service.py`、`tests/test_conversation_api.py` |
| `context-management` | `app/services/context_service.py`、必要的上下文配置 Schema 和配置默认值 | `tests/test_context_service.py` |
| `intent-routing` | `app/services/intent_service.py`、意图 Schema、必要的配置默认值 | `tests/test_intent_service.py` |
| `system-settings` | `app/api/admin_settings.py`、`app/services/settings_service.py`、`app/repositories/settings_repository.py`、`app/models/schemas/settings.py` | `tests/test_settings_service.py`、`tests/test_settings_api.py` |
| `llm-config` | `app/core/llm_factory.py`、`app/services/llm_config_service.py`、LLM 配置 Schema、必要的 settings 集成 | `tests/test_llm_config.py` |
| `knowledge-base` | `app/api/knowledge_base.py`、`app/services/knowledge_base_service.py`、`app/repositories/knowledge_repository.py`、`app/models/schemas/knowledge.py`、必要的 `app/services/vector_*` 适配 | `tests/test_knowledge_base_service.py`、`tests/test_knowledge_base_api.py` |
| `rag-pipeline` | `app/services/rag_pipeline_service.py`、`app/services/rerank/`、`app/models/schemas/rag.py`、必要的 vector search 适配 | `tests/test_rag_pipeline_service.py` |
| `citation` | `app/services/citation_service.py`、引用 Schema、引用持久化 Repository 方法 | `tests/test_citation_service.py` |
| `chat-runtime` | `app/api/chat.py`、`app/services/chat_runtime_service.py`、问答请求/响应 Schema、SSE 事件工具 | `tests/test_chat_runtime.py`、`tests/test_chat_stream.py` |
| `dashboard-metrics` | `app/api/admin_dashboard.py`、`app/services/dashboard_service.py`、`app/repositories/metrics_repository.py`、`app/models/schemas/dashboard.py` | `tests/test_dashboard_service.py`、`tests/test_dashboard_api.py` |
| `admin-frontend` | `static/index.html`、`static/app.js`、`static/styles.css`、必要时新增 `static/admin.html`、`static/admin.js` | 前端手工检查记录，必要时补 API 集成测试 |

共享文件修改规则：

- `app/main.py` 只能用于注册 Router、启动数据库初始化和挂载静态资源。
- `app/config.py` 只能追加兼容配置，不得删除已有配置字段。
- `app/api/chat.py` 是高冲突文件，除 `conversation` 和 `chat-runtime` 外的模块尽量不要修改。
- `static/*` 只由 `admin-frontend` 或明确负责前端集成的子 Agent 修改。
- 任何测试辅助夹具应优先放在 `tests/conftest.py`，但修改前必须确认不会破坏既有测试。

## 7.2 主 Agent 启动检查清单

开始实现前，主 Agent 必须执行并记录：

```powershell
git status --short
rg --files
python -m pytest
```

如果 `.venv` 存在，应优先使用：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

启动检查处理规则：

- 如果基线测试失败，先判断是否与本次任务相关。
- 与本次任务无关的既有失败不得通过删除测试或删除功能规避，应记录为基线问题。
- 与本次任务相关的失败必须在实现过程中修复。
- 如果依赖缺失导致测试无法启动，先根据项目依赖文件安装或记录环境阻塞，不得伪造测试通过。

## 7.3 子 Agent 交付审查清单

每个子 Agent 返回后，主 Agent 必须检查：

- 修改文件是否在授权写入范围内。
- 是否误改其他模块任务文件或无关文档。
- 是否引入真实 LLM、Milvus、外部网络依赖到单元测试。
- 是否新增了必要测试，并且测试命令真实运行过。
- 是否更新本模块 checklist 和完成记录。
- 是否破坏既有 API 兼容字段，例如 `Id`、`Question`、`/api/chat_stream`。
- 是否把密码、Token、API Key 或完整敏感配置写入日志、响应或测试快照。

若审查发现问题，主 Agent 应优先让同一子 Agent 在原写入范围内修复；若需要跨模块调整，由主 Agent 统一处理。

## 8. 文件与代码组织约束

后端建议遵循详细设计中的目录：

```text
app/
  api/
  auth/
  db/
  models/
    orm/
    schemas/
  repositories/
  services/
  core/
```

要求：

- API 层只做请求校验、鉴权依赖和响应封装。
- Service 层承载业务逻辑。
- Repository 层封装 SQLAlchemy 查询。
- ORM 模型和 Pydantic Schema 分离。
- 不在业务模块中直接读取 `.env` 中的动态配置，运行时配置统一走 `SettingsService`。
- LLM、Milvus、外部 API 调用必须可 Mock。
- 新增敏感信息不得写日志。
- 前端隐藏入口不作为权限控制，后端必须强制校验登录态。

## 9. 测试与检测要求

代码必须有完整单元测试并通过检测。

首选测试命令：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

如果虚拟环境不可用，使用：

```powershell
python -m pytest
```

针对单模块可先运行局部测试，例如：

```powershell
.\.venv\Scripts\python.exe -m pytest tests/test_auth_service.py
```

最终合并前必须运行完整测试：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

如果项目中可用，也运行：

```powershell
.\.venv\Scripts\python.exe -m ruff check app tests
```

如果 ruff 未安装，不要因此失败；记录为“ruff 不可用，已跳过”。pytest 失败必须修复，不能跳过。

测试原则：

- 数据库测试使用 SQLite 临时库或内存库。
- LLM 测试使用 Fake/Mock，不调用真实模型。
- Milvus 测试使用 Fake Vector Store 或 Mock Repository，不依赖真实 Milvus。
- SSE 测试验证事件结构和关键顺序。
- 权限测试必须覆盖未登录 401；会话归属等数据隔离场景可覆盖 403 或 404。
- 配置测试必须覆盖敏感字段脱敏。

## 9.1 分阶段测试矩阵

每个阶段完成后至少运行以下测试。若对应测试文件尚不存在，应先由相关模块补齐。

| 阶段 | 必跑测试 | 重点验证 |
| --- | --- | --- |
| 安全底座 | `tests/test_database_foundation.py`、`tests/test_auth_service.py`、`tests/test_auth_api.py` | 表创建、密码哈希、Token、未登录 401、已登录可访问管理接口 |
| 会话与运行时基础 | `tests/test_conversation_service.py`、`tests/test_context_service.py`、`tests/test_intent_service.py` | 会话归属、消息排序、上下文压缩、意图分类 |
| 配置、知识库与 RAG | `tests/test_settings_service.py`、`tests/test_llm_config.py`、`tests/test_knowledge_base_service.py`、`tests/test_rag_pipeline_service.py`、`tests/test_citation_service.py` | 动态配置、多知识库过滤、阈值、重排序降级、引用去重 |
| 问答主链路集成 | `tests/test_chat_runtime.py`、`tests/test_chat_stream.py` | SSE 顺序、消息落库、停止、重新生成、普通对话不检索 |
| 后台与统计 | `tests/test_dashboard_service.py`、`tests/test_dashboard_api.py`、后台接口相关测试 | 指标聚合、趋势补零、后台权限 |
| 最终验收 | 完整 `pytest`，可用时运行 `ruff check app tests` | 全局导入、API 注册、兼容接口、回归风险 |

## 9.2 最低可接受测试替身

外部依赖必须使用 Fake 或 Mock：

- LLM：使用 Fake Chat Model，支持返回固定文本、抛出异常、模拟流式分片。
- Embedding：使用固定维度的确定性向量，不调用真实 API。
- Milvus：使用内存 Fake Vector Store 或 Mock Repository，覆盖 metadata 过滤、删除和无结果场景。
- Reranker：使用 MockReranker，支持指定分数、超时异常和降级场景。
- 时间：涉及过期、趋势、排序时使用固定时间或可注入 clock，避免测试抖动。

不得为了测试方便绕过鉴权、会话归属校验或配置脱敏逻辑。

## 10. 无人参与规则

整个实现过程不会有人工参与。主 Agent 和子 Agent 遇到问题时按以下规则处理：

- 如果文档已有明确要求，严格按文档执行。
- 如果文档没有明确要求，但不影响外部契约，选择最小可行实现。
- 如果有多个技术方案，优先选择当前项目依赖最少、测试最容易、回滚风险最低的方案。
- 如果外部服务不可用，使用 Mock 或降级实现保证单元测试可运行。
- 如果某个模块被前置依赖阻塞，先完成可独立部分，并在任务文件记录未完成原因。
- 不要因为不确定而停下来等待用户。
- 不要引入生产级复杂设施来解决本期非目标问题，例如 Redis、Celery、SSO、多租户等，除非文档明确要求。

## 10.1 决策优先级

当文档、既有代码和测试之间出现冲突时，按以下优先级决策：

1. 安全边界优先：认证、权限、用户数据隔离不能退让。
2. 既有公开接口兼容优先：保留原路径、原字段兼容和原错误语义，除非安全要求必须改变。
3. 详细设计优先于任务清单：任务清单用于执行拆分，详细设计定义目标契约。
4. 可测试性优先于一次性复杂实现：先完成同步、单进程、可 Mock 的实现。
5. 最小变更优先：不做与当前模块无关的重构和风格调整。

如果必须偏离 `docs/design/detailed-design.md`，在对应任务文件底部新增：

```markdown
二级标题：实现备注

- 偏离点：
- 原因：
- 当前实现：
- 后续补齐：
```

## 10.2 兼容性红线

除非任务文件明确要求，否则不得删除或破坏以下既有能力：

- `/api/chat` 普通 JSON 问答。
- `/api/chat_stream` SSE 问答。
- `/api/chat/session/{session_id}` 历史查询兼容。
- `/api/chat/clear` 清理兼容。
- `/api/upload` 与 `/api/index_directory` 文件入库能力。
- `/api/badcases` 现有增强能力。
- 静态首页基础可访问。

新增鉴权后，这些业务接口未登录访问返回 401 属于预期变化；登录后原核心功能必须继续可用。

## 11. 进度维护规则

每完成一个最小任务：

- 更新对应 `docs/tasks/<module-name>.md` checklist。
- 如果只完成了部分实现，不要勾选完整 checklist；可追加更细 checklist 并只勾选真实完成项。

每完成一个模块：

- 确认该模块任务文件的完成标准已勾选。
- 更新 `docs/tasks/progress.md` 中模块完成情况。
- 在模块任务文件底部追加 `## 完成记录`，记录测试命令和结果。

每完成一个阶段：

- 运行阶段相关测试。
- 若阶段影响全局导入、鉴权、API 注册或数据库初始化，运行完整 pytest。

最终完成时：

- `docs/tasks/progress.md` 中所有模块应勾选完成，或明确记录未完成原因。
- 完整 pytest 必须通过。
- 输出最终总结。

## 11.1 完成记录格式

模块完成后，在对应任务文件底部追加：

```markdown
二级标题：完成记录

- 完成时间：YYYY-MM-DD
- 主要改动：
- 测试命令：
- 测试结果：
- 实现备注：
```

如果存在阻塞，追加：

```markdown
二级标题：未完成原因

- 未完成项：
- 阻塞原因：
- 已完成的降级实现：
- 后续最小补齐任务：
```

## 11.2 progress.md 更新规则

`docs/tasks/progress.md` 中模块勾选必须满足：

- 模块任务文件的“完成标准”全部勾选。
- 模块相关测试已运行并通过，或明确记录非代码环境阻塞。
- 该模块没有未解决的跨模块集成问题。

阶段勾选必须满足该阶段所有模块完成，并且阶段测试矩阵通过。

## 12. 兼容与迁移要求

改造时尽量保持已有接口兼容：

- `/api/chat`
- `/api/chat_stream`
- `/api/chat/session/{session_id}`
- `/api/chat/clear`
- `/api/upload`
- `/api/index_directory`
- `/api/badcases`

但新增鉴权后，业务接口未登录访问必须返回 401。

对旧字段的兼容：

- 当前聊天请求使用 `Id` 和 `Question`。
- 新请求应优先支持 `session_id` 和 `question`。
- 可以保留旧字段兼容一段时间。

## 13. 集成验收清单

最终必须满足：

- [ ] 未登录用户访问业务接口返回 401
- [ ] 未登录用户访问后台接口返回 401
- [ ] 已登录用户访问后台接口成功
- [ ] 用户可注册、登录、刷新 Token、退出登录
- [ ] 登录后可创建、查看、删除自己的会话
- [ ] 用户之间无法访问彼此会话和消息
- [ ] 普通闲聊不触发知识库检索
- [ ] 电力规范类问题进入 RAG Pipeline
- [ ] RAG 参数修改后下一次请求生效
- [ ] 知识问答可返回结构化引用
- [ ] 引用详情可查看来源文档、章节、切片和分数
- [ ] 无资料时不会伪造知识库依据
- [ ] 已登录用户可创建、启用、停用知识库
- [ ] 已登录用户可上传、重新索引、删除、下载文档
- [ ] 已登录用户可配置系统参数和模型参数
- [ ] 已登录用户可查看核心指标和近 30 天问答趋势
- [ ] 前端支持登录态、服务端会话、多行输入、停止生成、重新生成、思考过程、引用详情
- [ ] 完整 pytest 通过

## 13.1 手工验收脚本

最终实现完成后，除自动化测试外，主 Agent 应按以下顺序进行一次最小手工验收；如无法启动服务，必须记录原因。

1. 启动应用。
2. 未登录请求 `/api/chat`，确认返回 401。
3. 注册用户并登录，调用 `/api/auth/me` 确认登录态有效。
4. 未登录请求任一 `/api/admin/*` 接口，确认返回 401。
5. 用户创建会话、发送普通闲聊，确认不触发知识库检索。
6. 已登录用户创建知识库，上传测试文档并完成索引。
7. 发送知识问答，确认返回引用结构。
8. 修改 RAG Top K 或阈值，下一次问答确认配置生效。
9. 查看后台总览，确认核心指标非空且与测试数据基本一致。
10. 刷新页面或重新打开前端，确认登录态和服务端会话恢复正常。

## 13.2 最终风险检查

最终总结前必须主动检查：

- 是否有未提交或未说明的改动文件。
- 是否有新增测试未运行。
- 是否有敏感信息进入代码、日志、测试数据或文档。
- 是否有直接依赖真实外部服务的单元测试。
- 是否有接口只在前端隐藏、后端未做权限校验。
- 是否有多用户越权读取会话、消息、文档或统计数据的路径。
- 是否有 RAG 无资料时仍生成伪引用的路径。

## 14. 最终交付格式

完成全部实现后，主 Agent 输出：

```text
实现完成摘要：
- 完成的模块：
- 主要改动：
- 数据库与配置变更：
- API 变更：
- 前端变更：
- 测试命令与结果：
- 未完成项：
- 已知风险：
- 后续建议：
```

如果存在未完成项，必须说明：

- 未完成原因。
- 已完成的降级实现。
- 后续最小补齐任务。

## 15. 可直接使用的启动指令

当把本文档作为 Vibe Coding 起始 Prompt 使用时，主 Agent 应从以下动作开始：

```text
请阅读 docs/design/proposal.md、docs/design/detailed-design.md、docs/tasks/progress.md 和 docs/tasks/*.md。
然后执行 git status --short 和基线 pytest。
按 docs/design/implementation-prompt.md 的模块顺序推进实现。
每个模块完成后更新对应任务文件和 progress.md。
最终运行完整 pytest，并按 docs/design/implementation-prompt.md 的最终交付格式总结。
```

若执行环境支持子 Agent，则按本文第 7 节模板派发；若不支持子 Agent，则主 Agent 仍必须按模块边界顺序独立完成，不得把所有改动混在一个无边界实现中。

