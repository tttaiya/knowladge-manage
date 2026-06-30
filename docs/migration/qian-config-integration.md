# 钱小晓系统配置模块整合 — 7 个 Commit 合并指南

> **作者**：杨家杭（组长）
> **方案版本**：v6（最终版）
> **本文档独立成档**：不依赖之前任何方案
> **commit 范围**：`#14 ~ #20`（接程雨彤 13 个 commit 之后）

---

## 〇、整合方案与硬规则

整合方案见 `D:\桌面\钱小晓提交内容整合实施文档 v6（最终版）.docx`（87KB，13 章）。

本文档侧重**真实落盘状态**（与 v6 文档相比，落地时做了 6 项工程适配，见第三章）。

---

## 一、35 条硬规则（PR 评审 Checklist）

| 范围 | # | 规则 |
|---|---|---|
| v4 | R1-R8 | 见《实施方案 v4》 |
| v4 | R9 | Gateway 注入 `X-User-Id`，业务层不硬编码用户 ID |
| v4 | R10 | Java 8 兼容：`List.of / isBlank / var / HttpClient` 禁用 |
| v4 | R11 | 实体以 SQL 为准（`SystemConfig` 对齐 `km_system_config`）|
| v4 | R12 | SQL 字段加法迁移，不重命名 |
| v4 | R15 | Gateway 路由以 `application.yml` 为准 |
| v4 | R17 | SQL 放根 `sql/` 目录 |
| v4 | R18 | `/knowledge/` 部署 |
| v3 | R19 | 前端版本严格按设计文档 2.1 节（Vue 3.2.47 / Vite 3.2.5 / EP 2.2.27 / axios 1.3.5）|
| v3 | R20 | 前端 axios 必须同域 `/api/v1`，token 用 `access_token` |
| v3 | R21 | 钱小晓 ConfigController 不写 `@ExceptionHandler`，统一走 `GlobalExceptionHandler` |
| v3 | R22 | MQ 消息发布必须指定 Exchange：`convertAndSend(exchange, routingKey, msg)` |
| v3 | R23 | 跨服务动态配置走 `km.exchange + km.config.changed`；不存在的队列不允许预留 |
| v3 | R24 | 多条配置更新必须 `@Transactional` + MQ 发布走 `TransactionSynchronizationManager.afterCommit` |
| v4 | R25 | MQ 事件**永远不携带 API Key**（`collectSafeConfigValues()` 过滤 `*api_key*`）|
| v4 | R26 | RabbitMQ 全局 `Jackson2JsonMessageConverter`；Consumer 失败抛 `AmqpRejectAndDontRequeueException` → DLQ |
| v4 | R27 | Worker 启动必须调 km-admin 内部接口初始化 DynamicConfigHolder |
| v4 | R28 | 全局并发（`DynamicConfigHolder.maxConcurrentTasks`）与用户并发（`userQuota`）**双重限流** |
| v4 | R29 | RabbitMQ 拓扑修改必须同步 `infra/rabbitmq/definitions.json`（queue + binding + DLQ 三件套）|
| v5 | R30 | MyBatis 3.5.9 指 MyBatis Core；mybatis-spring-boot-starter 仍用 2.2.2 |
| v5 | R31 | RabbitMQ 配置消费者**单独** `configListenerContainerFactory`，ack=AUTO + retry + DLQ |
| v5 | R32 | `@RabbitListener(autoStartup="false")` + `RabbitListenerEndpointRegistry.start(id)` 显式启动；初始化失败 → 启动失败 |
| v5 | R33 | 内部接口 `GET /internal/km/configs/parser` + `X-Service-Token: ${INTERNAL_TOKEN}` |
| v6 | R34 | `km.dlx` **保持 topic**，禁止改为 fanout |
| v6 | R35 | `definitions.json` **必须增量修改**，仅删除 km.config.changed.search + 新增 worker.dlq + binding；其余全部保留 |
| v6 | R36 | docker-compose 中 service 名是 `rabbitmq` / `km-admin-service` / `km-worker-service`（`km-rabbitmq` 只是 `container_name`）|

---

## 二、7 个 Commit 清单

### Commit #14 — `chore(sql): add km_system_config table with 13 seed data, idempotent`

**新建文件**：`sql/km-system-config.sql`

**关键变更**：
- 新表 `km_system_config`（5 字段 + 1 唯一索引）
- **13 条 seed**（embedding 4 + rerank 5 + parser 4）
- 幂等：`ON DUPLICATE KEY UPDATE description=VALUES(description)`（**不覆盖** config_value）
- 默认值：api_base/api_key 空字符串；模型名/维度/TopN/阈值/并发/重试/超时保留有效值

**验证**：
```bash
docker compose -f infra/docker/docker-compose.demo.yml down -v && up -d --build
docker exec -it km-mysql mysql -ukm -pkm123456 km -e "SELECT COUNT(*) FROM km_system_config;"
# 期望 13
```

---

### Commit #15 — `feat(admin): integrate Qian config module into com.km.admin.config`

**新建文件（13 个）**：
```
backend-java/.../com/km/admin/config/
├── ConfigController.java                  # R21：删除 @ExceptionHandler
├── ConfigService.java                     # 接口
├── ConfigServiceImpl.java                 # R24：@Transactional + afterCommit
├── ConfigMapper.java                      # @Mapper
├── ConfigChangedProducer.java             # R22：convertAndSend("km.exchange", "km.config.changed", ...)
├── entity/SystemConfig.java               # 去 Lombok
├── dto/
│   ├── EmbeddingConfigDTO.java            # 去 Lombok
│   ├── RerankConfigDTO.java               # 去 Lombok
│   ├── ParserConfigDTO.java               # 去 Lombok
│   ├── ConnectionTestRequest.java         # 去 Lombok
│   ├── ConnectionTestResult.java          # 去 Lombok
│   └── ConfigChangedEvent.java            # R25：只携带非敏感配置 values
├── internal/InternalConfigController.java # R33：返回原始 DTO + ResponseStatusException
src/main/resources/mapper/ConfigMapper.xml # namespace 同步
```

**修改文件**：
- `backend-java/.../com/km/admin/AdminApplication.java` — `@MapperScan` 加 `com.km.admin.config`
- `backend-java/.../src/main/resources/application.yml` — 补 `km.internal.token=${INTERNAL_TOKEN}`；type-aliases-package 加 config.dto / config.entity
- **`backend-java/.../pom.xml` —— 移除 lombok 依赖 + 加 spring-boot-starter-test**

**关键修正（落地 vs v6 文档）**：
- `testConnection` 方法：v6 文档假设用 Java 11+ `HttpClient`，**违反 R10 Java 8 兼容**。落地改为只做 URL 格式校验（P0：未实发请求），避免引入 Java 11 API。
- 移除 admin pom.xml 的 `lombok 1.18.32` 依赖（项目代码实际**未使用** Lombok，去依赖避免污染）。

---

### Commit #16 — `feat(admin): transactional config update + afterCommit MQ publish + Jackson converter + Spring 集成测试`

**修改文件**：
- `ConfigServiceImpl.java` — 3 个 update 方法加 `@Transactional(rollbackFor = Exception.class)`；MQ 改 `TransactionSynchronizationManager.afterCommit` 发布
- `ConfigChangedProducer.java` — 改用 ObjectMapper **显式写 JSON 字节**发送（避免依赖 RabbitTemplate 全局 MessageConverter，影响其它走 SimpleMessageConverter 的 queue）

**关键修正（落地 vs v6 文档）**：
- v6 文档假设 Admin 配全局 `Jackson2JsonMessageConverter` Bean + Worker 配单独 `configListenerContainerFactory`。但**项目其它 queue 走 SimpleMessageConverter + 手写 JSON 字符串**，Admin 全局配 Jackson 会破坏现有 message body 序列化格式。
- 落地方案：Admin 端 `ConfigChangedProducer` 显式 `objectMapper.writeValueAsString` → `convertAndSend(exchange, routingKey, jsonBytes)`；Worker 端 `WorkerRabbitMqConfig` 配 `Jackson2JsonMessageConverter` + `configListenerContainerFactory`。两边协议清晰对齐。

**测试**：`src/test/java/com/km/admin/config/ConfigServiceTransactionalTest.java`
- `secondUpdateFails_rollbackAndNoPublish`：模拟第二条 update 抛异常 → 数据库整体回滚 + MQ 未被调用
- `embeddingUpdate_publishWithNullValues`：embedding 事件 `values=null`
- `parserUpdate_publishWithoutApiKey`：parser 事件 values 不含 `*api_key`

**注意**：此测试依赖 `@SpringBootTest` + 容器（MySQL / RabbitMQ），需 `docker compose up` 后才能跑。

---

### Commit #17a — `feat(worker): fix buggy refreshFromEvent + add id/autoStartup to 5 listeners`

**修改文件**：
- `backend-java/.../km-worker-service/src/main/java/com/km/worker/WorkerApplication.java`
  - 5 个 `@RabbitListener` 加 `id` + `autoStartup="false"`
  - `configChanged` 用 `containerFactory = "configListenerContainerFactory"`
  - 删除原文件内的 `class DynamicConfigHolder`（抽出到独立文件，避免 Bean 冲突）

**关键修正（落地 vs v6 文档）**：
- Worker 现有 `DynamicConfigHolder.refreshFromEvent` 用 `eventJson.contains("MAX_CONCURRENT_TASKS") + Integer.getInteger("MAX_CONCURRENT_TASKS", ...)` —— `Integer.getInteger` 读**系统属性**不是解析 JSON，**所以当前并发数永远不会真的更新**。bug 修复：用 ObjectMapper 解析 `event.values.parser.max_concurrent_tasks`。

**新增文件**：`backend-java/.../km-worker-service/src/main/java/com/km/worker/DynamicConfigHolder.java`
- 从 WorkerApplication.java 抽出
- 加 `initialized` 标志（R32：未初始化直接抛异常）
- 加 `setMaxConcurrentTasks` 范围校验（1..100）
- 重写 `refreshFromEvent`：ObjectMapper 解析 + 数值非法抛 `AmqpRejectAndDontRequeueException` → DLQ（R26）

---

### Commit #17b — `feat(worker): startup initialization + WorkerRabbitMqConfig + ConfigChangedEvent + ParserConfigResponse`

**新建文件（4 个）**：
```
backend-java/.../km-worker-service/src/main/java/com/km/worker/config/
├── ConfigChangedEvent.java                  # Worker 本地 POJO（与 admin 端同名字段对齐）
├── WorkerRabbitMqConfig.java                # R31：Jackson2JsonMessageConverter + configListenerContainerFactory (AUTO + retry + DLQ)
├── ConfigStartupInitializer.java            # R27/R32/R33：拉配置 + markInitialized + 启动 5 个 listener
└── ParserConfigResponse.java                # Worker 本地 DTO（不依赖 admin 模块）
```

**关键修正（落地 vs v6 文档）**：
- v6 文档把 `WorkerRabbitMqConfig` 放在 Worker 工程，但 `ConfigStartupInitializer` 最初引用 `com.km.admin.config.dto.ParserConfigDTO` —— 跨模块依赖违反 R33（Worker 不依赖 Admin 模块）。落地用 Worker 本地 `ParserConfigResponse`。
- `WorkerRabbitMqConfig.configListenerContainerFactory` 用 `RetryInterceptorBuilder.stateless().retryOperations(retry).recoverer(new RejectAndDontRequeueRecoverer()).build()` —— Spring AMQP 2.4.2 的 `SimpleRabbitListenerContainerFactory.setRecoveryCallback` 不存在（v6 文档示例用错 API）。
- 启动顺序：process → reprocess → reembed → purge → configChanged（v6 R32 修正）。

---

### Commit #18 — `chore(deploy): mount system-config.sql + incremental update definitions.json`

**修改文件**：
- `infra/docker/docker-compose.demo.yml`
  - MySQL volumes 追加 `12_km_system_config.sql`
  - `km-worker-service` 加 `INTERNAL_TOKEN: demo-internal-token`（与 admin 共用，R33）
- `infra/rabbitmq/definitions.json`
  - **增量修改（R35）**：
    - 删 `km.config.changed.search` queue + binding
    - 加 `km.config.changed.worker.dlq` queue
    - 改 `km.config.changed.worker` queue arguments：`x-dead-letter-exchange=km.dlx` + `x-dead-letter-routing-key=km.config.changed.worker.dlq`
    - 加 `km.dlx → km.config.changed.worker.dlq` binding（routing_key=`km.config.changed.worker.dlq`）
  - **保留**：所有 vhosts/users/permissions/原有 exchanges/queues/bindings

**验证**：
```bash
docker compose -f infra/docker/docker-compose.demo.yml config | grep "12_km_system_config"
docker exec -it km-rabbitmq rabbitmqctl list_queues -p /km name | grep config.changed
# 期望：worker + worker.dlq，无 search
docker exec -it km-rabbitmq rabbitmqctl list_bindings -p /km | grep config.changed
# 期望：km.exchange → worker (km.config.changed) + km.dlx → worker.dlq (km.config.changed.worker.dlq)
```

---

### Commit #19 — `feat(frontend): integrate Qian ConfigPage into knowledge-web`

**新建文件**：`frontend/knowledge-web/src/api/modules/config.ts`
- 使用现有 `request.ts`（同域 `/api/v1` + `access_token`）
- 接口签名 100% 保留钱小晓原版（`stripMaskedApiKey` 处理 `********` 掩码）

**修改文件**：`frontend/knowledge-web/src/views/knowledge/ConfigPage.vue`
- **替换占位**为钱小晓真实 371 行实现
- **隐藏 OCR 测试按钮**（P0：钱小晓原代码用 embeddingForm.apiBase 测 OCR 是错的）
- **默认 apiBase/apiKey 留空**（容器内 localhost 陷阱）
- 默认值修正：模型名 `text-embedding-v1` / 维度 1024 / TopN 10 / 阈值 0.7 / 并发 4 / 重试 3 / 超时 30

**遗留问题（环境，非本次整合引入）**：
本地 Node 22 + esbuild 0.15.18 不兼容（`npm install` postinstall 失败），无法跑 `npm run build`。**这与本次整合无关**——需要切换到 Node 16/18 后再验证 build。建议在 docker 容器内跑 build（已锁定 `engines.node=16.x`）。

---

### Commit #20 — `docs: Qian config integration migration guide`

本文档。

---

## 三、落地与 v6 文档的 6 项工程适配

| # | v6 文档假设 | 真实落地 | 原因 |
|---|---|---|---|
| 1 | Admin `testConnection` 用 Java 11+ `HttpClient` | 仅做 URL 格式校验（P0：未实发请求） | R10 Java 8 兼容 |
| 2 | Admin 全局 `Jackson2JsonMessageConverter` Bean | Producer **显式** ObjectMapper 写 JSON 字节发送 | 项目其它 queue 走 SimpleMessageConverter，全局 Converter 破坏现有格式 |
| 3 | `WorkerApplication.java` 单文件含 DynamicConfigHolder | DynamicConfigHolder 抽出到独立文件 | ConfigStartupInitializer 需 public 访问 |
| 4 | `ConfigStartupInitializer` import `com.km.admin.config.dto.ParserConfigDTO` | 用 Worker 本地 `ParserConfigResponse` | R33：Worker 不依赖 Admin 模块 |
| 5 | `SimpleRabbitListenerContainerFactory.setRecoveryCallback` | `RetryInterceptorBuilder.stateless().recoverer(new RejectAndDontRequeueRecoverer()).build()` + `setAdviceChain` | Spring AMQP 2.4.2 API |
| 6 | npm build 在前端 commit #19 内必须跑通 | 代码落盘 + TODO 注释 | 本地 Node 22 与项目 Node 16 不兼容，环境问题非代码问题 |

---

## 四、端到端验收清单（13 步）

```
1. 启容器：docker compose -f infra/docker/docker-compose.demo.yml down -v && up -d --build
2. 验证 SQL：SELECT COUNT(*) FROM km_system_config; 期望 13
3. 验证 RabbitMQ 拓扑：
   - rabbitmqctl list_vhosts 期望 /km
   - rabbitmqctl list_exchanges -p /km 期望 km.exchange topic + km.dlx topic（R34）
   - rabbitmqctl list_queues -p /km 期望 worker + worker.dlq；**无 search**
   - rabbitmqctl list_bindings 期望 km.exchange→worker (km.config.changed) + km.dlx→worker.dlq
4. 验证 km-admin-service：GET /api/v1/configs/embedding
   期望 apiBase='', apiKey='********', dimension=1024, model='text-embedding-v1'
5. 验证内部接口：GET /internal/km/configs/parser 带 X-Service-Token
   期望 maxConcurrentTasks=4
6. 验证事务回滚 + afterCommit（Spring 集成测试）：
   mvn -pl km-admin-service -Dtest=ConfigServiceTransactionalTest test
7. 验证 Worker 启动顺序：
   docker logs km-worker-service
   期望按顺序：ConfigStartupInitializer loaded → 5 个 listener started
8. 验证正常更新 + 不含敏感：
   PUT /api/v1/configs/parser maxConcurrentTasks=8
   docker logs km-worker-service 期望 maxConcurrentTasks=8
9. 验证 Worker 重启持久：
   docker restart km-worker-service
   期望 maxConcurrentTasks=8（不是默认值 2 或 4）
10. 验证启动失败行为：
    docker stop admin-service; docker restart worker-service
    期望 ConfigStartupInitializer 重试 5 次后 Worker 容器 exit
11. 验证 DLQ：
    手动 publish malformed event → 期望 AmqpRejectAndDontRequeueException → DLQ messages=1
12. 验证前端：
    （需先 npm run build 通过，见 commit #19 遗留问题）
13. 验证限流集成：
    启动 5 个解析任务，WorkerClaimService 读 holder.maxConcurrentTasks()（R28）
    期望前 N 个立即执行（N = 当前 maxConcurrentTasks）
```

---

## 五、已知风险与遗留

| 风险 | 等级 | 状态 |
|---|---|---|
| npm install postinstall 失败（Node 22 + esbuild 0.15.18） | 🟠 | 环境问题，需切换 Node 16/18 后重试 build |
| `ConfigServiceTransactionalTest` 需 docker compose up 才能跑 | 🟡 | 文档已说明 |
| Frontend 真实 UI 验证（步骤 12）需 build 通过 | 🟠 | 同上 |
| Worker 4 个 listener 现有 manual ACK 未切 AUTO | 🟢 | 文档要求 configChanged 单独 AUTO，其它保持 manual ACK，符合 R31 |

---

## 六、变更历史

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v6（最终）| 2026-06-29 | 用户确认 6 项修正后最终版 |
| 落地 | 2026-06-30 | 按 v6 实施，6 项工程适配（见第三章），7 个 commit 落盘 |