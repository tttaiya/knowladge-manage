# 邱子悦知识库管理模块整合 — 6 个 Commit 合并指南

> **作者**：杨家杭（组长）
> **方案版本**：v1.0
> **本文档独立成档**：不依赖之前任何方案
> **commit 范围**：`#27 ~ #32`（接钱小晓 7 个 commit、杨家凤 6 个 commit 之后）
> **方案文档**：`D:\桌面\知识管理模块F2整合实施步骤_杨家杭_v1.0.docx`（8 节 + 13 章）

---

## 〇、整合方案与硬规则

整合方案见 `知识管理模块F2整合实施步骤_杨家杭_v1.0.docx`。
本文档侧重**真实落盘状态**（与 v1.0 文档相比，落地时做了 5 项工程适配，见第三章）。

---

## 一、硬规则摘要

| 范围 | 规则摘要 |
|---|---|
| R12 | 字段加法迁移，不重命名（commit #27 V5 严格加法）|
| R14 | 全局用户 ID VARCHAR(36) UUID；km_document_process_task.user_id 已 V4 改 VARCHAR(36) |
| R15 | 前端 base `/knowledge/`，路径以 `application.yml` Gateway 路由为准 |
| R19/R20 | 前端 Vue 3.2.47 / Vite 3.2.5 / Element Plus 2.2.27 / axios 1.3.5（沿用主项目）|
| R22/R23 | MQ 发布必须指定 Exchange；KB 策略变更走 `km.exchange + km.doc.reprocess` |
| R26 | RabbitMQ 全局 `Jackson2JsonMessageConverter`；失败 → DLQ |
| R29 | definitions.json 已含 km.exchange + km.doc.* 拓扑（F2 复用，不改）|
| R34 | RabbitMQ DLX 保持 `km.dlx`（F2 不引入新队列）|
| F2 专属 | 知识库名称仅活动项目唯一（生成列 `active_name` + UNIQUE 索引 uk_kb_active_name）|
| F2 专属 | 策略变更必须 confirmation=true（不勾选 → 2004 Conflict）|
| F2 专属 | 删除前查 `km_document_process_task` 是否有 QUEUED/RUNNING → 2005 |
| F2 专属 | 批删任一失败 → 5001 整批回滚（不静默）|
| F2 专属 | 错误码：1001 参数/2004 状态/2005 在途/3001 任务已存在/5001 事务 |
| F2 专属 | 触发器：KB 策略变更 = KB 下每 doc 一条 REPROCESS 任务（idempotency_key = KB_REPROCESS:{kbId}:{strategyVersion}:{docId}）|

---

## 二、6 个 Commit 一览

| # | 主题 | 文件数 | 关键路径 |
|---|---|---|---|
| #27 | V5 加法迁移 | 1 SQL | `sql/V5__knowledge_base_management.sql` |
| #28 | admin knowledgebase 包 | 18 | `km-admin-service/.../knowledgebase/{controller,service,mapper,entity,dto,vo}/` + `common/BusinessException` + `application.yml` + `AdminApplication` |
| #29 | Task/Delete Facade 真实现 | 2 | `KnowledgeBaseTaskFacade.java` + `KnowledgeBaseDeleteFacade.java` |
| #30 | 前端 5 文件 + 路由 | 6 | `api/modules/knowledge-base.ts` + `types/knowledge-base.ts` + `views/knowledge/{KnowledgeBaseList,KnowledgeBaseDetail}.vue` + `components/knowledge/KnowledgeBaseFormDialog.vue` + `router/index.ts` |
| #31 | Compose V5 挂载 | 1 | `infra/docker/docker-compose.demo.yml` |
| #32 | 备份 + 测试 + docs | 3 | `docs/backup/qiuziyue-f2-20260630/` + `docs/migration/f2-integration.md` + 4 个 JUnit 测试 |

---

## 三、5 项工程适配（与 v1.0 文档相比）

### 1. 独立工程 → 整合入主项目

邱子悦原版是独立 SpringBoot 工程 `knowledge-management-jdk8`（含 `KnowledgeBaseApplication.java` 独立启动类、独立 `ApiResult`/`GlobalExceptionHandler`/`PageResult`、独立 `sql/knowledge-base.sql` 是 DROP TABLE）。
**整合策略**：
- 删除独立 `KnowledgeBaseApplication.java`（沿用主项目 `AdminApplication`）
- 删除独立 `ApiResult`/`PageResult`/`GlobalExceptionHandler`，改用主项目 `common/ApiResponse` + `common/PageResult` + 扩充后的 `common/GlobalExceptionHandler`
- 独立 `sql/knowledge-base.sql` 不用（主项目 `sql/knowledge-base.sql` 是基础表）；F2 加法迁移走 V5
- 全部 14 个 Java 类迁入 `com.km.admin.knowledgebase` 子包

### 2. UUID 与 BIGINT 冲突

邱子悦 `createdByUserId` 用 `BIGINT`（违反 R14）。**整合后改为 VARCHAR(36) UUID**，与 `km_knowledge_base.user_id`（已 V4 改 VARCHAR(36)）保持一致。`km_document_process_task.user_id` 也是 VARCHAR(36)，facade 写任务表用 `setString(3, userId)` 与 V4 对齐。

### 3. KB 粒度 → Doc 粒度任务展开

邱子悦原 `KnowledgeBaseService.reprocess` 只返回 `ReprocessResultVO` 含 `readyDocumentCount`，**不真创建任务**——这是 F2 v1.0 文档明确不允许的。

**整合后真实现**：
- `KnowledgeBaseTaskFacade` 自管 jdbc + RabbitMQ publish（不复用 `TaskCommandTxService`，因其是 package-private 不可跨包注入）
- 复用 `km_document.next_version_no` 行锁 + `km_document_version` 写表（复刻 `TaskCommandTxService.allocateBuildingVersion` 1:1 逻辑）
- idempotency_key = `KB_REPROCESS:{kbId}:{strategyVersion}:{docId}`，重入保护
- triggerSource = `KB_STRATEGY_CHANGE`（与已有 `STRATEGY_CHANGE` 区分），routingKey 沿用 `km.doc.reprocess`

### 4. 错误码重映射

| 邱子悦原版 | 整合后 | 场景 |
|---|---|---|
| 4000 | 1001 | 参数校验 |
| 2003 | 2004 | 业务状态不允许 |
| — | 2005 | 在途任务 |
| — | 3001 | 任务已存在 |
| — | 5001 | 事务异常 |

`GlobalExceptionHandler` 新增 `BusinessException` 分支（按 code 路由 400/409/500），保留 `IllegalArgumentException` → 1001、`IllegalStateException` → 2004。

### 5. 名称唯一性改造

v1.0 文档建议 `UNIQUE KEY uk_kb_name (name)`。**实际主项目 km_knowledge_base 已有数据，name 列也允许软删后复用**——直接加 uk_kb_name 会破坏现有数据。

**整合方案**：加 GENERATED STORED 列 `active_name = CASE WHEN is_deleted=0 THEN name ELSE NULL END`，对 `active_name` 建 UNIQUE KEY `uk_kb_active_name`。效果：仅活动知识库 name 唯一；软删后允许同名复用。

---

## 四、13 步验收

| 步 | 验证项 | 命令 / 路径 |
|---|---|---|
| 1 | V5 迁移在 init 期间执行 | `docker compose up -d` 后 `docker exec km-mysql mysql -ukm -pkm123456 -e "DESCRIBE km_knowledge_base"` |
| 2 | uk_kb_active_name 索引存在 | `SHOW INDEX FROM km_knowledge_base` |
| 3 | admin 编译通过 | `mvn -pl km-admin-service compile`（注：完整 BUILD 依赖 docker 起 MySQL/Rabbit 镜像，本地纯 compile 不验数据源）|
| 4 | /api/v1/admin/knowledge-bases 列表 | curl `GET /api/v1/admin/knowledge-bases?pageNum=1&pageSize=10` |
| 5 | 名称唯一校验 | POST 同名活动 KB → 1001 |
| 6 | 软删后同名复用 | DELETE → POST 同名 → 200 |
| 7 | 策略变更 confirmation | PUT 策略字段无 confirmation → 2004；带 true → 触发 N 条 task |
| 8 | 在途任务拦截删除 | 触发 reprocess 期间 DELETE → 2005 |
| 9 | 批删任一失败回滚 | batch-delete 含不存在 ID → 5001，全 DB 无变化 |
| 10 | RabbitMQ REPROCESS 派发 | management UI `km.exchange` → `km.doc.reprocess` 队列有新消息 |
| 11 | 前端 /knowledge/bases | 列表 + 分页 + 分类筛选 |
| 12 | 前端 /knowledge/bases/:kbId | 详情 + 字段分隔符渲染 |
| 13 | 前端 弹窗策略变更 confirmation | 编辑 KB 改策略 → 勾选框强制勾选 |

---

## 五、已知限制 / 后续 TODO

1. **documentCount 触发器未实现**：当前仅 mapper 提供 `incrementDocumentCount / decrementDocumentCount`，未在 `DocumentTaskFacade` 中调用。**F3 文档模块整合时**需在 `DocumentService.createDocument` / `RecycleBinService.delete` 处接入。
2. **前端 npm build 失败**（与 F2 无关）：Node 22 + esbuild 0.15.18 不兼容，F4 整合时遗留。
3. **F2 错误码 3001 未触发**：当前 `idempotencyKey` 直接返回已存在 taskId，不抛 3001。后续若需"严格拒绝重入"语义，把 facade 的 `existingTaskId != null` 改为抛 `BusinessException.taskAlreadyExists` 即可。
4. **knowledgeBaseSnapshot 序列化为 Object**：facade 内部用 `ObjectMapper.convertValue(snapshot, Map.class)`，但 task_payload_json 在 worker 端反序列化时 `Object` 类型会变 `LinkedHashMap`——worker 端需按需 `convertValue` 回 `KnowledgeBaseSnapshotVO`。

---

## 六、文件清单（commit #32）

```
docs/backup/qiuziyue-f2-20260630/                        # 51 文件（邱子悦独立工程源码备份）
docs/migration/f2-integration.md                         # 本文件
backend-java/.../admin/src/test/.../knowledgebase/       # 4 JUnit 测试
```

---

## 七、Commit 信息模板

```
#27 F2: V5 knowledge_base 加法迁移（description/category/...+active_name 生成列+uk_kb_active_name）
#28 F2: admin knowledgebase 包 14 类 + 1 XML + 1 错误码 handler + 1 BusinessException + application.yml + AdminApplication
#29 F2: KnowledgeBaseTaskFacade 真实现（jdbc+publish+allocateVersion） + KnowledgeBaseDeleteFacade（2005/5001 完整）
#30 F2: 前端 5 文件 + 路由 /bases/:kbId
#31 F2: docker-compose V5 挂载（13_V5__knowledge_base_management.sql）
#32 F2: 备份 docs/backup/qiuziyue-f2-20260630 + 4 测试 + docs/migration/f2-integration.md
```
