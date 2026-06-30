# database-foundation：数据库基础设施与 ORM 模型

目标：引入 `SQLite/PostgreSQL + SQLAlchemy` 持久化底座，为认证、会话、知识库、配置和统计模块提供统一数据访问能力。

## 最小任务

- [x] 新增 `app/db/session.py`，提供 SQLAlchemy engine、session factory 和 FastAPI DB 依赖
- [x] 新增 `app/db/base.py`，集中导出 ORM Base 和所有模型注册入口
- [x] 在配置中新增 `database_url`，开发默认使用 SQLite，保留 PostgreSQL 连接串支持
- [x] 新增数据库初始化函数，应用启动时可创建缺失表
- [x] 新增 `app/models/orm/user.py`，定义 `users`、`refresh_tokens`
- [x] 新增 `app/models/orm/conversation.py`，定义 `chat_sessions`、`chat_messages`、`chat_message_citations`
- [x] 新增 `app/models/orm/knowledge.py`，定义 `knowledge_bases`、`knowledge_documents`、`knowledge_chunks`
- [x] 新增 `app/models/orm/settings.py`，定义 `system_settings`
- [x] 新增 `app/models/orm/metrics.py`，定义 `qa_metrics`
- [x] 为所有表补齐 `created_at`、`updated_at`、必要的 `deleted_at`
- [x] 为用户名、知识库编码、配置键增加唯一约束
- [x] 为 `user_id`、`session_id`、`knowledge_base_id`、`document_id` 增加常用索引
- [x] 新增基础 Repository 父类或通用 CRUD 辅助方法
- [x] 新增 `tests/test_database_foundation.py`，验证 SQLite 内存库可创建全部表
- [x] 新增测试验证唯一约束生效
- [x] 新增测试验证 `updated_at` 在更新时变化

## 完成标准

- [x] 应用启动时数据库连接可用
- [x] 测试环境可使用 SQLite 独立运行
- [x] ORM 模型字段覆盖详细设计中的核心表
- [x] 不依赖 Milvus、LLM 或前端即可完成本模块测试

## 完成记录

- 新增 SQLAlchemy 2.0 风格数据库基础设施：`Base`、统一时间戳字段、SQLite/PostgreSQL engine 创建、`SessionLocal`、`get_db()` 与 `init_db()`。
- 新增认证、会话、知识库、系统配置、问答统计相关 ORM 表，并补齐唯一约束、外键和常用查询索引。
- 新增通用 `BaseRepository`，提供 `get`、`list`、`add`、`delete` 基础 CRUD 辅助方法。
- 新增独立 SQLite 内存库单元测试，覆盖全部表创建、唯一约束和 `updated_at` 自动更新。
- 验证命令：`.\.venv\Scripts\python.exe -m pytest tests/test_database_foundation.py`，结果：`3 passed`。
