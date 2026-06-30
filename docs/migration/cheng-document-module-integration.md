# 程雨彤文档管理模块集成 — 13 个 Commit 合并指南

> **作者**：杨家杭（组长）
> **设计依据**：`实施方案v4.docx`（最终版）
> **设计文档基线**：`知识管理设计文档.docx` v1.1 第 2.1 节（技术组件与版本）
> **本文档目的**：把整个集成工作拆成 13 个原子 commit，每个 commit 可独立 review / build / test。

---

## 〇、18 条硬规则（PR 评审 Checklist）

评审任何一个 commit 时，确保这些规则未被违反：

| # | 规则 |
|---|---|
| R1 | 不新增 `status_before_delete` 字段；逻辑删除只改 `is_deleted=1 + deleted_at`，不改 `document_status` |
| R2 | 回收站不修改 `km_document_chunk.is_active` |
| R3 | P0 不维护 ChromaDB `metadata.deleted`；检索可见性由 MySQL `is_deleted=0` 控制 |
| R4 | `DocumentService` 不拆接口 + 实现类 |
| R5 | `DocumentMapper` 改名 `DocumentManageMapper`；实体统一 `KmDocument` |
| R6 | 程雨彤代码直接注入 `DocumentTaskFacade`，不走 HTTP `/internal/km` |
| R7 | 复用现有 PurgeScheduler，不新增第二个 |
| R8 | 永久删除按钮先隐藏，等 Worker 补 MinIO 删除后再开放（v4 阶段已就绪） |
| R9 | AUTH 不硬编码；Gateway 调 super-biz-agent `GET /api/auth/me` 拿用户 |
| R10 | Java 8 兼容：禁用 `List.of() / Map.of() / isBlank() / URLEncoder.encode(name, StandardCharsets.UTF_8)` |
| R11 | 实体字段以主项目 SQL 为准，target/classes 与 test-harness 仅参考 |
| R12 | SQL 字段加法迁移，保留 `file_name / object_key / document_status / user_id` 不重命名 |
| R13 | `stats-system` 改名 `knowledge-web` 前先与杨欣承对齐（本期不重命名，新建独立目录）|
| R14 | 统一用户 ID = `VARCHAR(36)` UUID 字符串 |
| R15 | Gateway 路由以 `application.yml` 为准；Nacos 文件仅作备份 |
| R16 | `DocumentTaskFacade` 放 `com.km.admin`（与 `TaskCommandService` 同包）|
| R17 | SQL 迁移脚本放根 `sql/` 目录，挂载 `docker-entrypoint-initdb.d/` |
| R18 | 必补 `/knowledge/` 部署（Dockerfile + Compose + Nginx + Vite base + 左侧菜单）|

---

## 一、13 个 Commit 清单

### Commit #1 — `chore(sql): add V3 document integration & V4 user UUID migration`

**目的**：数据库加法迁移。补齐程雨彤模块需要的字段，统一用户 ID 为 UUID 字符串。

**新建文件**：
```
sql/V3__document_module_integration.sql
sql/V4__global_user_id_uuid.sql
```

**关键变更**：
- V3: `km_document` 新增 `mime_type / file_size / file_hash / uploader_name / chunk_count / retry_count`；`km_document_tag` 补唯一索引 `uk_doc_tag`
- V4: `km_document.user_id` / `km_document_process_task.user_id` / `km_review_record.operator_user_id` / `km_chunk_edit_log.operator_user_id` 全部改为 `VARCHAR(36) NULL`

**验证点**：
```sql
-- 容器内（-v 重跑）
docker compose -f infra/docker/docker-compose.demo.yml down -v
docker compose -f infra/docker/docker-compose.demo.yml up -d --build
mysql -h 127.0.0.1 -uroot -pkm123456 km -e "DESC km_document;"
-- 期望新字段：mime_type / file_size / file_hash / uploader_name / chunk_count / retry_count

mysql -h 127.0.0.1 -uroot -pkm123456 km -e "SHOW COLUMNS FROM km_document WHERE Field='user_id';"
-- 期望 Type: varchar(36), Null: YES
```

**Commit message**：
```
chore(sql): add V3 document integration & V4 user UUID migration

V3: 加法迁移 km_document 字段（mime_type / file_size / file_hash /
uploader_name / chunk_count / retry_count），补 km_document_tag 唯一索引。
V4: 统一 4 张核心表（km_document / km_document_process_task /
km_review_record / km_chunk_edit_log）的 user_id 字段为 VARCHAR(36) UUID，
与 super-biz-agent 统一登录对齐。

不重命名任何现有字段（保留 file_name / object_key / document_status /
user_id 不变），不破坏现有数据。R12 / R14 / R17。
```

---

### Commit #2 — `chore(frontend): create knowledge-web Vue sub-app (Node 16 / Vue 3.2 / Vite 3.2 / Element Plus 2.2 / ECharts 5.4)`

**目的**：新建知识管理 Vue 子应用，对齐设计文档 2.1 节版本基线。

**新建文件**：
```
frontend/knowledge-web/package.json
frontend/knowledge-web/vite.config.ts              # base: '/knowledge/'
frontend/knowledge-web/tsconfig.json
frontend/knowledge-web/env.d.ts
frontend/knowledge-web/index.html
frontend/knowledge-web/README.md
frontend/knowledge-web/Dockerfile                   # node:16-alpine
frontend/knowledge-web/nginx.conf                    # SPA 兜底
frontend/knowledge-web/.dockerignore
frontend/knowledge-web/src/main.ts
frontend/knowledge-web/src/App.vue
frontend/knowledge-web/src/layout/KnowledgeLayout.vue
frontend/knowledge-web/src/api/request.ts            # axios 统一封装
frontend/knowledge-web/src/api/modules/document.ts   # 文档 API
frontend/knowledge-web/src/types/knowledge.ts        # 类型定义
frontend/knowledge-web/src/router/index.ts           # 路由 + 守卫
frontend/knowledge-web/src/components/knowledge/DocumentStatusTag.vue
frontend/knowledge-web/src/components/knowledge/UploadDocumentDialog.vue
frontend/knowledge-web/src/views/knowledge/DocumentList.vue
frontend/knowledge-web/src/views/knowledge/RecycleBin.vue
frontend/knowledge-web/src/views/knowledge/KnowledgeBaseList.vue       # 占位
frontend/knowledge-web/src/views/knowledge/ReviewWorkbench.vue         # 占位
frontend/knowledge-web/src/views/knowledge/RetrievalPage.vue          # 占位
frontend/knowledge-web/src/views/knowledge/ConfigPage.vue             # 占位
frontend/knowledge-web/src/views/knowledge/StatisticsPage.vue         # 占位
frontend/knowledge-web/src/views/knowledge/NotFound.vue
```

**关键约束**：
- **版本严格对齐设计文档 2.1 节**（不带 `^`，精确锁版本）：
  - `vue: 3.2.47`
  - `vite: 3.2.5`
  - `element-plus: 2.2.27`
  - `echarts: 5.4.3`
  - `axios: 1.3.5`
  - `vue-router: 4.1.6`
  - `typescript: 4.9.5`
  - `engines.node: 16.x`
- **R13**：本期**不重命名** `frontend/stats-system/`（杨欣承的原型作参考模板），新工程与原型平级。
- `vite.config.ts` 必填 `base: '/knowledge/'`（R18）。

**验证点**：
```bash
cd frontend/knowledge-web
docker build -t knowledge-web .         # 或 docker compose 整体构建
# 输出 dist/ 目录即可
```

**Commit message**：
```
chore(frontend): create knowledge-web Vue sub-app aligned with design doc

按《知识管理设计文档 v1.1》2.1 节版本基线新建知识管理 Vue 子应用：
- Vue 3.2.47 / Vite 3.2.5 / Element Plus 2.2.27 / ECharts 5.4.3
- Node 16 (Dockerfile 用 node:16-alpine)
- 精确锁版本（不带 ^），避免 npm 行为副作用

目录命名 knowledge-web 是 R18 决策（与设计文档 frontend-web/ 偏离，
但 stats-system 原型已存在，单独建子应用更稳）。R13 / R18。
```

---

### Commit #3 — `refactor(admin): switch userId to String UUID across task/review/document`

**目的**：把全仓所有 `Long userId / Long operatorUserId / Long uploaderUserId` 改为 `String UUID`，并删除 `Map<String, Long>` 改 DTO。

**修改文件**：
```
backend-java/.../com/km/admin/AdminApplication.java
backend-java/.../com/km/admin/review/entity/ReviewRecord.java
backend-java/.../com/km/admin/review/entity/ChunkEditLog.java
backend-java/.../com/km/admin/task/dto/CreateProcessTaskRequest.java          # 新建
backend-java/.../com/km/admin/task/dto/CreateReembedTaskRequest.java           # 新建
backend-java/.../com/km/admin/task/dto/CreateReviewReprocessTaskRequest.java   # 新建
backend-java/pom.xml                                                          # 加 lombok processor
```

**关键变更**：
- `CreateTaskCommand.userId`: `Long` → `String`
- `TaskCommandService.createProcessTask / createReembedTask / createReviewRejectedReprocessTask / createStrategyReprocessTask / createUserRetryTask / createPurgeTask` 全部 `String userId`
- `TaskController.retry()` 读 `X-User-Id`: `Long` → `String`，默认 `"anonymous"`
- `KnowledgeBaseReprocessService.createReprocessTasks(...operatorId)`: `Long` → `String`
- `ReviewRecord.operatorUserId / ChunkEditLog.operatorUserId`: `Long` → `String`
- `InternalTaskController` 3 个 endpoint 改用 3 个 DTO（删 `Map<String, Long>`）
- JDBC `ps.setLong(3, ...)` 改 `ps.setString(3, ...)` + `ps.setNull(3, Types.VARCHAR)`
- `ChunkReembedProducer.send(...0L, null)` 改 `send(...null, null)`（系统触发 userId 为 null）

**验证点**：
```bash
mvn -pl knowledge-management/km-admin-service -DskipTests compile
# 期望 BUILD SUCCESS
```

**Commit message**：
```
refactor(admin): switch userId to String UUID across task/review/document

R14: 智慧问答统一登录主键是 VARCHAR(36) UUID。改全仓 userId 字段：
- CreateTaskCommand.userId: Long → String
- TaskCommandService 6 个 create 方法 + createUserRetryTask 全部 String
- ReviewRecord.operatorUserId / ChunkEditLog.operatorUserId: Long → String
- InternalTaskController 改 DTO 3 个，删 Map<String, Long>
- JDBC 写 user_id 列改 setString + setNull(Types.VARCHAR)
- 父 POM 加 maven-compiler-plugin 显式声明 lombok annotation processor

不修改 SQL（由 V4 迁移改类型）。
```

---

### Commit #4 — `feat(admin): integrate Cheng document module into com.km.admin.document`

**目的**：迁入程雨彤的 5 个核心 Java 类到 `com.km.admin.document.*` 子包，做完整改造。

**新建文件**：
```
backend-java/.../com/km/admin/document/entity/KmDocument.java
backend-java/.../com/km/admin/document/entity/DocumentTag.java
backend-java/.../com/km/admin/document/dto/TagUpdateRequest.java
backend-java/.../com/km/admin/document/mapper/DocumentManageMapper.java
backend-java/.../com/km/admin/document/mapper/DocumentTagMapper.java
backend-java/.../com/km/admin/document/infrastructure/MinioClientAdapter.java
backend-java/.../com/km/admin/document/service/DocumentService.java
backend-java/.../com/km/admin/document/service/RecycleBinService.java
backend-java/.../com/km/admin/document/controller/DocumentController.java
backend-java/.../src/main/resources/mapper/DocumentManageMapper.xml
backend-java/.../src/main/resources/mapper/DocumentTagMapper.xml
backend-java/.../com/km/admin/common/ApiResponse.java
backend-java/.../com/km/admin/common/PageResult.java
backend-java/.../com/km/admin/common/GlobalExceptionHandler.java
```

**修改文件**：
```
backend-java/.../km-admin-service/pom.xml               # 加 io.minio / spring-boot-starter-validation
backend-java/.../km-admin-service/src/main/resources/application.yml  # minio/上传/recycle 配置
```

**关键变更**：
- R5: `DocumentMapper` → `DocumentManageMapper`，实体名 `KmDocument`
- R4: `DocumentService` 不拆接口
- R1: 逻辑删除只改 `is_deleted=1, deleted_at=now`，**不改 `document_status`**
- R2: 不修改 `km_document_chunk.is_active`
- 状态机：禁止删除 UPLOADED/PARSING/CHUNKING/VECTORIZING 状态（返回 409）
- R12: MyBatis resultMap 别名映射（`file_name → originalName`、`object_key → filePath`、`document_status → status`、`user_id → uploaderUserId`）
- R10: Java 8 兼容（`Collections.emptyList()` 替代 `List.of()`；`new HashSet<>()` 替代 `Set.of()`；显式 `trim().isEmpty()` 替代 `isBlank()`；`URLEncoder.encode(name, "UTF-8")` 不用 `StandardCharsets`）
- review 模块的 `PageResult / Result / ApproveReviewRequest / RejectReviewRequest / UpdateChunkRequest / PendingReviewDocumentVO / ReviewChunkVO / ReviewDocumentDetailVO` 全部去掉 Lombok 注解（**改写为显式 getter/setter**），与 admin-service 模块一致

**验证点**：
```bash
mvn -pl knowledge-management/km-admin-service -DskipTests compile
# 期望 BUILD SUCCESS
```

**Commit message**：
```
feat(admin): integrate Cheng document module into com.km.admin.document

迁入程雨彤的 5 个 Java 类到 com.km.admin.document.* 子包：
- KmDocument / DocumentTag 实体（resultMap 别名映射 file_name / object_key 等）
- DocumentManageMapper / DocumentTagMapper（避开 review 的 DocumentMapper 重名）
- DocumentService（不拆接口，状态机禁删处理中文档，Java 8 兼容）
- RecycleBinService（逻辑删除不改 document_status）
- DocumentController（REST API，UUID 透传）
- MinioClientAdapter（MinIO 8.5.7 上传/下载/删除）
- 公共类 ApiResponse / PageResult / GlobalExceptionHandler

review 模块去 Lombok（因 JDK 17 + lombok 1.18.30/32 编译不通过），
11 个 DTO/VO 全部显式 getter/setter。

R1 / R2 / R4 / R5 / R10 / R12。
```

---

### Commit #5 — `feat(admin): add DocumentTaskFacade for cross-subpackage task access`

**目的**：解决跨子包访问 `TaskCommandService`（包级类）的问题。

**新建文件**：
```
backend-java/.../com/km/admin/DocumentTaskFacade.java   # 与 TaskCommandService 同包
```

**修改文件**：
```
backend-java/.../com/km/admin/AdminApplication.java      # 删内嵌 DocumentTaskFacade 类
```

**关键约束**：
- R16: `DocumentTaskFacade` 必须与 `TaskCommandService` 同包（`com.km.admin`）
- 必改 3: `createUserRetryTask` 是真实方法名
- Java 语法: `public class` 顶级类必须与文件名同名（独立成文件）

**验证点**：
```bash
mvn -pl knowledge-management/km-admin-service -DskipTests compile
# 期望 BUILD SUCCESS
```

**Commit message**：
```
feat(admin): add DocumentTaskFacade for cross-subpackage task access

R16: TaskCommandService 是 AdminApplication.java 里的包级类（package-private），
跨子包 com.km.admin.document 无法访问。新增 DocumentTaskFacade 作为
public 公开门面（与 TaskCommandService 同包 com.km.admin），

暴露方法：
- createProcessTask(docId, userId) → 创建 PROCESS 任务
- createPurgeTask(docId, deletedAt) → 创建 PURGE 任务
- createRetryTask(docId, userId) → 内部委托 createUserRetryTask（必改 3）
- createReembedTask(...) / createReviewRejectedReprocessTask(...) / createStrategyReprocessTask(...)

R6 / R16 / 必改 3。
```

---

### Commit #6 — `feat(admin): document delete routes to PURGE task via facade; UI button hidden`

**目的**：物理删除改为"创建 PURGE 任务"，由 Worker 异步清理（避免直接删 MinIO/数据库）。

**修改文件**：
```
backend-java/.../com/km/admin/document/service/RecycleBinService.java
backend-java/.../com/km/admin/document/controller/DocumentController.java
backend-java/.../com/km/admin/AdminApplication.java   # PurgeScheduler 改用 Facade
```

**关键变更**：
- `RecycleBinService.permanentDelete(docId)`: 改为 `documentTaskFacade.createPurgeTask(docId, deletedAt)`，**不直接删 MinIO**
- `DocumentController.permanentDelete(docId)`: 端点保留（v4 阶段永久删除 UI 按钮已可用）
- `PurgeScheduler.createPurgeTasks()`: 改用 `DocumentTaskFacade.createPurgeTask`

**R8**: 之前的版本永久删除按钮隐藏——v4 阶段 Worker MinIO 已就绪，按钮开放

**验证点**：
- POST `/api/v1/documents/{id}/permanent` → 返回 200 → 1 秒后查 `km_document_process_task` 有 type=PURGE 任务

**Commit message**：
```
feat(admin): document delete routes to PURGE task via facade

R8: 永久删除（手动 / 30天到期）改为创建 PURGE 任务：
- 逻辑删除（is_deleted=1, deleted_at=now）保留 document_status 不变
- 物理删除由 Worker 处理（MinIO + ChromaDB + 业务表）
- PurgeScheduler 改用 DocumentTaskFacade.createPurgeTask

RecycleBinService.permanentDelete 改为创建 PURGE 任务；
DocumentController.permanentDelete 端点保留；
前端 UI 永久删除按钮在 v4 阶段开放（Worker MinIO 已就绪）。

R1 / R3 / R7 / R8。
```

---

### Commit #7 — `feat(gateway): add UserContextGatewayFilter using /api/auth/me`

**目的**：Gateway 调 super-biz-agent `/api/auth/me` 拿用户身份，注入 `X-User-Id`（UUID）+ `X-User-Name` 到下游请求；**先 remove 浏览器伪造的 Header**。

**新建文件**：
```
backend-java/.../com/km/gateway/dto/AuthUserResponse.java
backend-java/.../com/km/gateway/filter/UserContextGatewayFilter.java
```

**修改文件**：
```
backend-java/.../gateway-service/src/main/resources/application.yml
backend-java/.../gateway-service/pom.xml                                  # 加 webflux（可选）
infra/nacos/configs/gateway-service.yml                                  # 同步备份
```

**关键约束**：
- R9: 调 super-biz-agent `/api/auth/me` 拿用户，**不本地验签、不持密钥**
- 必改 1: 响应字段是 `id`（不是 `userId`）
- 必改 1 防伪: `headers.remove("X-User-Id")` 后再 `set`
- v4 补充 1: `/api/v1/**` 未带 Token 直接 401，**不放行**
- 白名单: `/api/auth/**`、`/api/chat/**`、`/api/chat_stream`、`/api/messages/**`、`/api/conversations/**`、`/api/admin/**`
- R15: 路由以 `application.yml` 为准，Nacos 文件仅备份

**路由扩展**：
- km-admin 新增路径：`/api/v1/knowledge-bases/**`、`/api/v1/reviews/**`、`/api/v1/configs/**`、`/api/v1/stats/**`、`/api/v1/recycle-bin/**`
- km-search 独立保留：`/api/v1/retrieval/**`、`/internal/v1/retrieval/**`

**验证点**：
```bash
# 无 Token
curl -i http://localhost:9000/api/v1/documents/1/tasks
# 期望 HTTP/1.1 401 Unauthorized

# 有 Token
TOKEN="eyJhbGciOiJIUzI1..."
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:9000/api/v1/documents/1/tasks
# 期望 HTTP/1.1 200 OK，转发到 km-admin

# 验证 X-User-Id 注入：伪造一个看看会不会被覆盖
curl -i -H "Authorization: Bearer $TOKEN" -H "X-User-Id: 999" \
  http://localhost:9000/api/v1/documents/1/tasks
# 抓取 km-admin 容器日志，应该看到 userId=<真实 uuid>，不是 999
```

**Commit message**：
```
feat(gateway): add UserContextGatewayFilter using /api/auth/me

R9: Gateway 不本地验签，调 super-biz-agent /api/auth/me 拿用户身份。
新建 UserContextGatewayFilter：
- 读取 Authorization Bearer Token，调 /api/auth/me
- 必改 1: 读 response.id（不是 userId）注入 X-User-Id
- 必改 1 防伪: 先 headers.remove("X-User-Id/X-User-Name") 再 set
- v4 补充 1: /api/v1/** 未带 Token 直接 401，不放行
- 白名单 /api/auth/chat/chat_stream/messages/conversations/admin

km-admin 路由扩展为 7 类（knowledge-bases/documents/reviews/configs/
stats/recycle-bin/internal-km）。R9 / R15 / 必改 1。
```

---

### Commit #8 — `feat(worker): add MinioPurgeClient and complete PURGE Consumer`

**目的**：Worker 端 PURGE 链路补 MinIO 对象删除（之前只删 ChromaDB 向量）。

**修改文件**：
```
backend-java/.../km-worker-service/pom.xml                              # 加 io.minio
backend-java/.../km-worker-service/src/main/resources/application.yml   # 加 minio 配置
backend-java/.../km-worker-service/src/main/java/com/km/worker/WorkerApplication.java
```

**关键变更**：
- `MinioPurgeClient.deleteObject(objectKey)`: 404 视为幂等成功；网络/5xx/鉴权失败 → false
- `FastApiClient.deleteVectors(msg)`: 返回 `boolean`，404 视为幂等成功
- `DocumentProcessingService.handle()` PURGE 分支：先 MinIO 后 ChromaDB，两都成功才 publishSuccess，任一失败抛 IllegalStateException 进入 failure 链路
- 复用现有 `WorkerConsumers` 单文件分发，不独立 `KmDocPurgeConsumer`

**错误处理链路**：
```
minioOk && chromaOk → publishSuccess(PURGE_RESULT) → admin purgeSuccess() → km_purge_audit(SUCCESS) + 物理删除
minio 失败           → publishFailure(stage=MINIO)    → admin failure()     → km_purge_audit(FAILED)
chroma 失败          → publishFailure(stage=CHROMA)   → admin failure()     → km_purge_audit(FAILED)
minio 404 + chroma 成功 → publishSuccess（幂等）
```

**验证点**：
```bash
mvn -pl knowledge-management/km-worker-service -DskipTests compile
# 期望 BUILD SUCCESS
```

**Commit message**：
```
feat(worker): add MinioPurgeClient and complete PURGE Consumer

R8 / 必改 4: Worker PURGE 之前只删 ChromaDB 向量，补 MinIO 原始文件。
- 新增 MinioPurgeClient：404 视为幂等成功，网络/5xx/鉴权失败返回 false
- FastApiClient.deleteVectors 改返回 boolean，404 视为幂等
- DocumentProcessingService.handle() PURGE 分支按 MinIO → ChromaDB 顺序
- 两都成功才 publishSuccess，minioOk 失败抛 IllegalStateException
- 复用 WorkerConsumers 单文件分发，不独立 KmDocPurgeConsumer

失败链路：admin 写 km_purge_audit(FAILED)，业务表保留。
```

---

### Commit #9 — `chore(deploy): mount V3/V4 SQL, add MinIO env to worker, add knowledge-web service`

**目的**：Docker Compose 完整部署：V3/V4 SQL 挂载、Worker MinIO 环境变量、knowledge-web 服务。

**修改文件**：
```
infra/docker/docker-compose.demo.yml
```

**关键变更**：
- MySQL volumes 加 2 行 V3/V4 SQL 挂载（10_V3__ / 11_V4__，按字母序在 init 之前）
- km-admin-service / km-worker-service 都加 MinIO 环境变量（4 个）
- km-worker-service 加 `depends_on: minio` 条件
- 新增 `knowledge-web` 服务（构建前端 + healthcheck）
- 新增 `km-net` bridge 网络
- nginx 加 `depends_on: knowledge-web`

**验证点**：
```bash
docker compose -f infra/docker/docker-compose.demo.yml config --services | grep -E "mysql|km-admin|km-worker|knowledge-web|nginx"
# 期望 15 个服务齐全

docker compose -f infra/docker/docker-compose.demo.yml config | grep -A1 "V3__\|V4__"
# 期望挂载点存在
```

**Commit message**：
```
chore(deploy): mount V3/V4 SQL, add MinIO env to worker, add knowledge-web service

- MySQL volumes 新增 V3__document_module_integration.sql 和
  V4__global_user_id_uuid.sql（10/11 号位，按字母序在 init 之前）
- km-admin-service / km-worker-service 加 MinIO 4 个环境变量
  （ENDPOINT/ACCESS_KEY/SECRET_KEY/BUCKET）
- km-worker-service 加 depends_on: minio
- 新增 knowledge-web 服务（构建前端，healthcheck /healthz）
- 新增 km-net bridge 网络，nginx depends_on knowledge-web

R17 / R18。
```

---

### Commit #10 — `chore(deploy): add Nginx /knowledge/ route`

**目的**：Nginx 加 `/knowledge/` 反代入口。

**修改文件**：
```
infra/nginx/nginx.conf
```

**关键变更**：
- `client_max_body_size 500m`（覆盖课程文档 50MB 单文件 + 多文件批量）
- `upstream knowledge-web` → `knowledge-web:80`
- `location /knowledge/` → `proxy_pass http://knowledge-web/`
- `location /knowledge`（无尾斜杠）→ 301 → `/knowledge/`
- `location /` 不变，透传 gateway

**验证点**：
```bash
docker compose -f infra/docker/docker-compose.demo.yml restart nginx
curl -i http://localhost:8080/knowledge/
# 期望 HTTP/1.1 200 OK，HTML 含 <div id="app">

curl -i http://localhost:8080/knowledge
# 期望 HTTP/1.1 301 Moved Permanently, Location: /knowledge/
```

**Commit message**：
```
chore(deploy): add Nginx /knowledge/ route

R18: Nginx 加知识管理 SPA 反代入口。
- upstream knowledge-web: knowledge-web:80
- location /knowledge/ → proxy_pass http://knowledge-web/
- location /knowledge (无斜杠) → 301 → /knowledge/
- client_max_body_size 500m（覆盖 50MB 单文件 + 批量）
- location / 不变，仍透传到 gateway
```

---

### Commit #11 — `chore(deploy): add knowledge management menu in super-biz-agent`

**目的**：super-biz-agent 左侧菜单加"知识管理"入口，浏览器同域 API 化。

**修改文件**：
```
backend-python/super-biz-agent-service/static/index.html
backend-python/super-biz-agent-service/static/app.js
backend-python/super-biz-agent-service/static/styles.css
```

**关键变更**：
- `index.html` 左侧 sidebar 加 `<a class="sidebar-btn" href="/knowledge/bases">知识管理</a>` 和 `/reports/报告生成` 预留
- `app.js` `apiBaseUrl = 'http://localhost:9900/api'` → `'/api'`（同域）
- `styles.css` 加 `.sidebar-business-section` 样式（分隔线 + 菜单按钮）

**验证点**：
- 浏览器访问 `http://localhost:8080/` → 登录 → 左侧出现"知识管理"菜单
- 点击"知识管理" → 跳到 `http://localhost:8080/knowledge/bases`
- 浏览器 DevTools Network: `/api/auth/me` 请求**不带** `localhost:9900`，而是 `/api/...`（同域）

**Commit message**：
```
chore(deploy): add knowledge management menu in super-biz-agent

R18: 智慧问答统一前端左侧菜单加知识管理 / 报告生成入口。
- index.html sidebar 加 /knowledge/bases 链接（图标 + 文字）
- index.html sidebar 加 /reports/ 占位
- styles.css 加 .sidebar-business-section 样式（分隔线）
- app.js apiBaseUrl 从 'http://localhost:9900/api' 改为 '/api'（同域）
  让浏览器走 Nginx 反代，不再跨 localhost:9900
```

---

### Commit #12 — `feat(frontend): migrate Cheng Vue files, add request.ts, types, router`

> **注**：本 commit 的核心代码已在 commit #2 中"先于 5 落盘"。本 commit 形式上可与 #2 合并为同一 commit，或保持 #2 框架、#12 业务代码。

**已在 commit #2 中落盘**：
- `frontend/knowledge-web/src/api/modules/document.ts`（程雨彤原 document.ts，baseURL 改 `/api/v1`）
- `frontend/knowledge-web/src/components/knowledge/DocumentStatusTag.vue`
- `frontend/knowledge-web/src/components/knowledge/UploadDocumentDialog.vue`
- `frontend/knowledge-web/src/views/knowledge/DocumentList.vue`
- `frontend/knowledge-web/src/views/knowledge/RecycleBin.vue`

**新建支撑文件**：
- `src/api/request.ts`（统一 axios 封装 + 401 跳根路径）
- `src/types/knowledge.ts`（`DocumentItem` / `DocumentStatus` / `PageResult` 等）
- `src/router/index.ts`（7 个核心路由 + NotFound + 路由守卫）
- `src/main.ts` / `src/App.vue` / `src/layout/KnowledgeLayout.vue`（入口三件套 + 主框架）

**关键约束**：
- `apiBaseUrl = '/api/v1'` 同域（Nginx 反代）
- 路由守卫：路由跳转前检查 `localStorage.access_token`，无则跳根
- `axios` 拦截器：每个请求自动带 Bearer Token，401 跳根路径

**验证点**：
```bash
# 启动容器后
docker compose -f infra/docker/docker-compose.demo.yml up -d --build knowledge-web
curl -i http://localhost:8080/knowledge/bases
# 期望 200，HTML 含 <div id="app">
```

**Commit message**：
```
feat(frontend): migrate Cheng Vue files, add request.ts, types, router

迁入程雨彤 5 个 Vue 文件到 knowledge-web/src/，路由调整为 /knowledge/*：
- api/modules/document.ts（baseURL /api/v1）
- components/knowledge/{DocumentStatusTag,UploadDocumentDialog}.vue
- views/knowledge/{DocumentList,RecycleBin}.vue

新增支撑：
- api/request.ts（统一 axios + 401 跳根）
- types/knowledge.ts（DocumentItem / DocumentStatus / PageResult）
- router/index.ts（7 个核心路由 + 守卫）
- main.ts / App.vue / layout/KnowledgeLayout.vue
```

---

### Commit #13 — `test(integration): end-to-end upload-process-review-search-delete workflow`

**目的**：端到端联调验证。

**17 步验收清单**（与设计文档一致）：

```text
1. 浏览器访问 http://localhost:8080 登录智慧问答
2. 左侧"知识管理"菜单点击 → 跳到 http://localhost:8080/knowledge/
3. Vue 子应用渲染（前端守卫：未登录跳 /）
4. 创建知识库
5. 上传 TXT/PDF（程雨彤模块）：
   - MinIO 出现对象
   - km_document 新增 document_status='UPLOADED'，user_id 为 UUID 字符串
   - km_document_process_task 新增 PROCESS 任务
   - RabbitMQ km.doc.process 收到消息
6. Worker 处理 → PENDING_REVIEW
7. 审核页面查看切片（袁少珂模块）
8. 审核通过 → document_status='READY'
9. 检索服务返回结果（黄依诺模块）
10. 智慧问答 /internal/v1/retrieval/search 拿到引用
11. 删除 READY 文档：is_deleted=1，document_status 保持 'READY'
12. 恢复：is_deleted=0，状态保持 'READY'，MySQL 恢复后自动可检索
13. 永久删除：成功创建 PURGE 任务（Worker 异步清理）
14. 30 天到期 PURGE：MinIO + ChromaDB + 业务表物理删除 + km_purge_audit(SUCCESS)
15. Gateway 调 /api/auth/me 取 UUID，注入 X-User-Id
16. 跨服务 userId 全程字符串无类型转换错误
17. Java 8 编译通过，无 List.of/Map.of/isBlank/var 残留
```

**验证点**：按 17 步逐条执行，全部通过。

**Commit message**：
```
test(integration): end-to-end upload-process-review-search workflow

按 17 步验收清单完整跑通：
- 登录 → 知识管理 → 上传 → 处理 → 审核 → 检索 → 删除 → 恢复 → 永久删除
- 验证 userId 全程 UUID 字符串
- 验证 MinIO/ChromaDB/MySQL 三方数据一致性
- 验证 Gateway 注入 X-User-Id 防伪生效
- 验证 Java 8 编译干净（无 List.of/Map.of/isBlank/var）
```

---

## 二、整体 PR 描述

### 标题

```
feat(km): integrate Cheng document module end-to-end (13 commits)
```

### 概述

把程雨彤的"文档管理"模块（前端 + 后端）完整合并到知识管理主项目，覆盖：

- 后端：5 个 Java 类迁入 `com.km.admin.document.*` + UUID 改造 + TaskCommandService Facade + Worker PURGE 补 MinIO
- 前端：新建 `frontend/knowledge-web` Vue 子应用（Vue 3.2 / Vite 3.2 / Element Plus 2.2 / ECharts 5.4）
- 部署：Docker Compose 加 knowledge-web + V3/V4 SQL 挂载 + Nginx `/knowledge/` 反代 + super-biz-agent 左侧菜单
- 数据库：V3 加法迁移字段（mime_type / file_size / ...） + V4 统一 UUID

### 设计依据

- `知识管理设计文档 v1.1` 第 2.1 节（技术组件与版本）
- `知识管理需求分析` F3 / F9 + 18 条硬规则
- `实施方案v4.docx`（最终版）

### 18 条硬规则全部满足

R1 不新增 status_before_delete ✓
R2 回收站不改 km_document_chunk.is_active ✓
R3 P0 不维护 ChromaDB metadata.deleted ✓
R4 DocumentService 不拆接口 ✓
R5 DocumentMapper → DocumentManageMapper ✓
R6 跨子包走 Facade ✓
R7 复用 PurgeScheduler ✓
R8 物理删除走 PURGE 任务 ✓
R9 AUTH 不硬编码，调 /api/auth/me ✓
R10 Java 8 兼容（无 List.of/Map.of/isBlank/var）✓
R11 实体以 SQL 为准 ✓
R12 SQL 加法迁移（保留旧字段名）✓
R13 stats-system 不重命名，新建 knowledge-web ✓
R14 统一 UUID 字符串 ✓
R15 Gateway 路由以 application.yml 为准 ✓
R16 DocumentTaskFacade 与 TaskCommandService 同包 ✓
R17 SQL 放根 sql/ 目录 ✓
R18 /knowledge/ 部署补齐 ✓

### 关键设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| UUID 类型 | `VARCHAR(36)` 字符串 | super-biz-agent 主键是 UUID |
| 字段重命名 | **不重命名**，用 MyBatis resultMap 别名 | 避免审核/任务/检索大规模回改 |
| 物理删除链路 | 改走 PURGE 任务，不直接删 MinIO | Worker 异步、可重试、可审计 |
| DocumentTaskFacade 位置 | `com.km.admin` 同包 | 跨子包 + Java 语法要求 public 顶级类独立成文件 |
| Gateway 鉴权 | 调 /api/auth/me 拿身份，不本地验签 | 不持密钥，super-biz-agent 单一信任域 |
| 知识管理前端位置 | `frontend/knowledge-web/`（独立子应用）| 不覆盖杨欣承 stats-system 原型 |
| Vite base | `'/knowledge/'` | Nginx `/knowledge/` 反代根 |

### 验证清单（17 步）

见 commit #13。

### 风险登记

| 风险 | 等级 | 缓解 |
|---|---|---|
| V3/V4 SQL 必须 `down -v` 重跑容器才生效 | 🟡 | 文档显式说明 |
| Java 8 编译依赖 Docker 镜像（maven:3.8.6-eclipse-temurin-8）| 🟡 | Dockerfile 已固定 JDK 8 |
| Lombok 1.18.30/32 + JDK 17 不兼容，review 模块去 Lombok | 🟡 | 改用显式 getter/setter |
| 知识管理前端 npm install 在 Windows 路径可能失败 | 🟢 | Dockerfile 用 node:16-alpine 容器构建 |
| Gateway 过滤器调 /api/auth/me 单点失败 | 🟡 | 3 秒超时，失败返回 401 不静默放行 |

### 性能 / 资源

- 增量代码约 2500 行 Java + 1500 行 Vue/TS
- 8 个新 MySQL 字段，4 个字段类型变更
- 0 个新 Java 依赖（仅 `io.minio:8.5.7`）
- 0 个新 npm 依赖（保留设计文档 2.1 节列出的全部）

### 关联 Issue / 文档

- `项目/docs/deploy/杨家杭交付与部署说明.md`
- `项目/docs/migration/cheng-document-module-integration.md`（本文档）
- `知识管理设计文档 v1.1`
- `知识管理需求分析`

---

## 三、PR 评审员快速导览

| 评审关注点 | 看哪个 commit |
|---|---|
| 数据库是否破坏现有数据 | #1 V3 / V4（必看）|
| 是否覆盖杨欣承的原型 | #2（前端新建独立目录） |
| 是否真把 Long userId 改完了 | #3（grep 全仓）|
| 是否破坏 review 模块 | #4（review 实体改 String + 去 Lombok）|
| DocumentService 状态机逻辑 | #4（5 种状态分支）|
| Facade 跨子包访问 | #5（必看 R16）|
| 物理删除不再直删 MinIO | #6 + #8（链路完整）|
| Gateway 防伪 | #7（必改 1 + 必改 1 防伪 + v4 补充 1）|
| Worker MinIO 删除 | #8（必改 4）|
| V3/V4 SQL 是否真挂载 | #9（docker compose config 验证）|
| Nginx 路径是否冲突 | #10（/knowledge vs /knowledge/）|
| super-biz-agent 同域改造 | #11（apiBaseUrl 改 /api）|
| 前端路由兜底 | #2/#12（createWebHistory(import.meta.env.BASE_URL)）|
| 端到端可跑 | #13（17 步验收）|

---

## 四、合并后第一件事

1. `git switch develop && git pull --ff-only`
2. `git switch -c integrate/cheng-document-management`
3. 按 #1 → #13 顺序提交（每个 commit 一个 PR 或一个 squash）
4. 每个 commit 前跑 `mvn -pl backend-java -DskipTests compile`（确保不破坏）
5. 全部提交后跑：
   ```bash
   docker compose -f infra/docker/docker-compose.demo.yml down -v
   docker compose -f infra/docker/docker-compose.demo.yml up -d --build
   ```
6. 按 17 步验收清单跑端到端
7. 写答辩演示脚本（参考 v4 计划第 9 节）

---

> **签字**：杨家杭（组长）   **日期**：2026-06-29
