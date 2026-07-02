# 电力技术监督智能辅助平台｜知识管理模块

> 面向电力技术监督场景的知识库与文档智能处理模块。系统作为统一“智慧问答”门户中的独立业务子模块运行，复用门户的登录态与用户身份；用户可从左侧导航进入“知识管理”。

[![Java](https://img.shields.io/badge/Java-Spring%20Boot%202.6.4-brightgreen)](#)
[![Vue](https://img.shields.io/badge/Frontend-Vue%203%20%2B%20Vite-42b883)](#)
[![Python](https://img.shields.io/badge/AI-FastAPI%20%2B%20Python%203.10-009688)](#)
[![Middleware](https://img.shields.io/badge/Middleware-RabbitMQ%20%7C%20Nacos%20%7C%20MinIO-orange)](#)

---

## 1. 项目简介

本模块用于管理电力专业知识库、技术文档及其处理结果，并为智慧问答、报告生成等上层能力提供可审核、可追溯的知识检索服务。

系统以“**上传 → 异步处理 → 解析/OCR → 切片 → 向量化 → 审核 → 可检索**”为主链路。文档未经审核通过前，不进入正式检索范围；审核通过后才可被检索服务召回并参与问答。

### 核心能力

- 知识库、分类及标签管理
- 文档上传、列表管理、回收站与 MinIO 对象存储
- Word / PDF 等文档解析、必要时 OCR、文本切片与元数据保留
- RabbitMQ 驱动的异步任务处理、Worker 抢占、租约与结果回写
- 向量化、ChromaDB 存储、重排序与知识检索
- 审核工作台：文档审核、切片查看与内容编辑
- 系统配置：模型、解析策略、并发等配置项管理
- 统计看板：文档、任务、知识库等统计与趋势展示

### 当前边界

- 知识管理模块依赖统一智慧问答门户完成认证，不单独提供登录页面。
- 文档删除采用逻辑删除：更新 `is_deleted`、`deleted_at`，不直接修改原文档处理状态。
- 审核通过前的文档不应参与正式 ChromaDB 检索。
- 单文件最大 **50 MB**；单次最多上传 **10 个文件**；单用户同时最多 **2 个**文档处理任务。

---

## 2. 系统架构

```text
┌──────────────────────────────────────────────────────────────┐
│                    统一智慧问答门户 / Vue 前端                 │
│       登录态复用 · 知识库 · 文档 · 审核 · 配置 · 统计           │
└───────────────────────────────┬──────────────────────────────┘
                                │ HTTP / JWT
┌───────────────────────────────▼──────────────────────────────┐
│                         Gateway Service                       │
│                  路由转发 · 统一入口 · 鉴权协同                │
└───────────────┬───────────────────────────┬──────────────────┘
                │                           │
       ┌────────▼────────┐         ┌────────▼────────┐
       │ km-admin-service│         │km-search-service │
       │ 知识库/文档/审核 │         │ 检索/召回/重排序  │
       │ 配置/统计/结果落库│         │                  │
       └────────┬────────┘         └────────┬────────┘
                │                             │
                │                      ┌──────▼───────┐
                │                      │  ChromaDB    │
                │                      │ 向量集合/检索 │
                │                      └──────────────┘
       ┌────────▼────────┐
       │km-worker-service│
       │任务抢占/租约/重试│
       └────────┬────────┘
                │ RabbitMQ
       ┌────────▼──────────────────────────────────────────────┐
       │          FastAPI AI 文档处理服务（F4 / F5）              │
       │  解析 · OCR · 切片 · 向量化 · 重排序 · 内部接口鉴权       │
       └────────────────────────────────────────────────────────┘

MySQL  ·  Redis  ·  RabbitMQ  ·  Nacos  ·  MinIO  ·  ChromaDB
```

---

## 3. 代码结构

以下结构以当前项目代码组织为准，个别前端目录名以仓库实际目录为准：

```text
.
├── backend-java/
│   ├── platform-parent/              # Maven 父工程与依赖版本管理
│   ├── common/                       # 公共 DTO、异常、工具与通用能力
│   ├── gateway-service/              # 网关与统一路由
│   └── knowledge-management/
│       ├── km-admin-service/         # 知识库/文档/审核/配置/统计/任务结果落库
│       ├── km-worker-service/        # 异步任务消费、抢占、租约、重试与清理
│       └── km-search-service/        # 检索、向量召回、重排序与内部检索接口
├── <vue-frontend>/                   # Vue 3 + Vite 知识管理前端子应用
├── <fastapi-ai-service>/             # FastAPI 解析、OCR、切片、向量化服务
├── docker-compose.yml                # 本地中间件/服务编排（以仓库实际文件为准）
├── docs/                             # 需求、设计、接口、测试等文档
└── README.md
```

---

## 4. 功能模块

| 模块 | 主要职责 |
|---|---|
| F2 知识库管理 | 知识库 CRUD、分类、基础信息维护 |
| F3 文档管理 | 上传、列表、标签、逻辑删除、回收站、MinIO 文件管理 |
| F4 文档处理 | 文档解析、OCR、文本提取、切片及结构化元数据输出 |
| F5 检索与向量化 | Embedding、ChromaDB 写入、向量召回、重排序 |
| F6 系统配置 | 模型、解析策略、并发等运行时配置管理 |
| F8 统计看板 | 文档、知识库、任务状态与趋势统计 |
| F9 审核工作台 | 文档审核、切片查看/编辑、审核后入库控制 |
| Worker | PROCESS / REPROCESS / REEMBED / PURGE 等异步任务处理 |

---

## 5. 核心业务流程

### 5.1 文档处理与审核主链路

```text
用户上传文件
  → MinIO 暂存原文件
  → km-admin 创建文档与处理任务
  → 事务提交后投递 RabbitMQ 消息
  → km-worker 抢占任务并持有租约
  → FastAPI 执行解析 / OCR / 切片
  → 向量化服务生成 Embedding 并写入 ChromaDB
  → 处理结果发布至 km.task.result
  → km-admin 幂等消费结果并在单事务内落库
  → 审核人员审核文档与切片
  → 审核通过，文档 READY 并进入正式检索范围
```

### 5.2 异步任务原则

- 任务创建应在数据库事务提交后投递消息，避免“数据库未提交但消息已消费”。
- Worker 消费前应完成任务抢占；执行中通过租约防止同一任务被多个 Worker 重复处理。
- 处理结果统一写入结果队列，再由管理服务进行幂等消费和单事务落库。
- 对可恢复故障执行有限重试；无法恢复的任务应保留失败原因并进入后续人工处理或 DLQ 流程。
- 重发扫描应基于最后投递/尝试时间判断，避免仅按创建时间导致误重投。

### 5.3 主要任务主题

> 队列、交换机和绑定关系以项目中的 RabbitMQ Definitions 与服务配置为最终准则。

| 主题 / 队列 | 用途 |
|---|---|
| `km.doc.process` | 新上传文档的解析、切片与处理任务 |
| `km.doc.reprocess` | 文档重新处理任务 |
| `km.chunk.reembed` | 切片重新向量化任务 |
| `km.doc.purge` | 回收站文档物理清理任务 |
| `km.config.changed` | 动态配置变更通知与刷新 |
| `km.task.result` | 处理服务向管理服务回传统一处理结果 |

---

## 6. 网关路由

当前网关以 `backend-java/gateway-service/src/main/resources/application.yml` 为准，主要路由如下：

| 路径前缀 | 下游服务 |
|---|---|
| `/api/v1/admin/knowledge-bases/**` | `km-admin-service` |
| `/api/v1/knowledge-bases/**` | `km-admin-service`（文档模块嵌套路由） |
| `/api/v1/documents/**` | `km-admin-service` |
| `/api/v1/reviews/**` | `km-admin-service` |
| `/api/v1/configs/**` | `km-admin-service` |
| `/api/v1/stats/**` | `km-admin-service` |
| `/internal/km/**` | `km-admin-service` |
| `/api/v1/retrieval/**` | `km-search-service` |
| `/internal/v1/retrieval/**` | `km-search-service` |

> 对外接口统一通过 Gateway 访问。`/internal/**` 为服务间内部接口，应使用内部令牌保护，禁止向公网直接暴露。

---

## 7. 技术栈

| 层级 | 技术 |
|---|---|
| 前端 | Vue 3.2、Vite 3.2、Element Plus 2.2、ECharts 5.4 |
| Java 后端 | Spring Boot 2.6.4、Spring Cloud 2021.0.1、Spring Cloud Alibaba / Nacos、MyBatis 3.5.9 |
| AI 服务 | FastAPI、Python 3.10、文档解析 / OCR / Embedding / Rerank |
| 数据存储 | MySQL 5.7+、Redis、MinIO、ChromaDB |
| 消息与注册 | RabbitMQ 3.11.11、Nacos |
| 部署 | Docker Compose、Nginx |
| 暂不启用 | Elasticsearch 7.12.1 |

---

## 8. 本地运行

### 8.1 前置条件

请先准备以下运行环境：

- JDK 与 Maven（版本以父工程配置为准）
- Node.js 与 npm / pnpm（以当前前端 lock 文件为准）
- Python 3.10 与 pip
- Docker Desktop / Docker Compose
- MySQL、Redis、RabbitMQ、Nacos、MinIO、ChromaDB

### 8.2 配置中间件与环境变量

推荐优先使用项目的 Docker Compose 启动本地依赖：

```bash
docker compose up -d
```

随后在 Nacos 配置中心或各服务的本地配置文件中，补齐以下配置项。不要将真实密钥、生产数据库密码或第三方模型密钥提交到 Git。

| 配置项 | 说明 |
|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DATABASE` | MySQL 连接信息 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | MySQL 账号与密码 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | RabbitMQ 连接信息 |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ 账号与密码 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET` | MinIO 对象存储配置 |
| `CHROMA_HOST` / `CHROMA_PORT` / `CHROMA_COLLECTION` | ChromaDB 服务与集合配置 |
| `EMBEDDING_API_BASE` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL` | 向量模型配置 |
| `RERANK_API_BASE` / `RERANK_API_KEY` / `RERANK_MODEL` | 重排序模型配置 |
| `INTERNAL_TOKEN` | Java 服务与 FastAPI 服务间内部调用令牌 |
| `FASTAPI_BASE_URL` | FastAPI 文档处理服务地址 |
| `NACOS_SERVER_ADDR` | Nacos 地址 |

### 8.3 初始化数据库

1. 在项目中定位数据库迁移或初始化 SQL 文件。
2. 创建本地数据库并按迁移顺序执行。
3. 确认服务账号对业务库具有建表、读写及事务权限。
4. 启动服务前检查 MySQL、RabbitMQ、Nacos、MinIO、Redis、ChromaDB 均可访问。

### 8.4 启动 Java 服务

在 `backend-java` 目录中先完成构建：

```bash
mvn clean install -DskipTests
```

推荐启动顺序：

```text
1. gateway-service
2. km-admin-service
3. km-search-service
4. km-worker-service
```

开发环境可通过 IDE 直接运行各服务的 Spring Boot 启动类；命令行启动时，请以对应模块的 `pom.xml`、Nacos 配置和实际 profile 为准。

### 8.5 启动 FastAPI 文档处理服务

在 FastAPI 服务目录中创建虚拟环境并安装依赖：

```bash
python -m venv .venv
# Windows PowerShell
.\.venv\Scripts\Activate.ps1
# macOS / Linux
# source .venv/bin/activate

pip install -r requirements.txt
```

按服务实际入口启动，例如：

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

当前 F4 解析服务内部端口为 `8000`，Docker 环境下宿主机映射按项目编排配置执行（当前集成约定为宿主机 `8004`）。除 `/health` 外，内部解析接口须校验 `X-Internal-Token`。

### 8.6 启动前端

进入实际 Vue 前端目录后执行：

```bash
npm install
npm run dev
```

前端开发代理、统一登录跳转地址和 Gateway 地址请以 `vite.config.*`、环境文件及部署配置为准。

---

## 9. 健康检查与联调建议

### 最小验收闭环

1. 登录统一智慧问答门户，进入左侧“知识管理”。
2. 创建知识库并上传真实 DOCX / PDF 文档。
3. 确认原文件已写入 MinIO，文档与任务记录已落库。
4. 检查 `km.doc.process` 是否被 Worker 消费，任务状态是否按阶段推进。
5. 验证 FastAPI 的解析、必要时 OCR、切片返回是否包含结构化元数据。
6. 验证向量化结果是否写入 ChromaDB，且未审核文档不会被正式检索。
7. 在审核工作台通过文档后，验证检索接口可召回相应内容。
8. 制造一次可控失败，确认失败原因、重试、幂等结果消费和状态更新符合预期。

### 切片元数据要求

为支持审核、检索定位和重向量化，F4/F5 输出应尽量保留以下字段：

```json
{
  "chunkIndex": 0,
  "chapterPath": "第一章/1.1",
  "pageNo": 1,
  "chunkType": "paragraph",
  "charCount": 256
}
```

---

## 10. 常见问题

### 文档已上传但始终未处理

依次检查：

1. `km-admin-service` 是否成功创建文档与处理任务。
2. 事务提交后是否成功投递 `km.doc.process`。
3. `km-worker-service` 是否在线并能完成任务抢占。
4. RabbitMQ 队列、消费者连接、死信队列与消息堆积情况。
5. `FASTAPI_BASE_URL`、`INTERNAL_TOKEN`、MinIO 临时文件路径是否一致。
6. FastAPI 服务日志中是否存在解析、OCR、权限校验或模型调用失败。

### 文档处理完成但无法检索

检查审核状态是否已通过；确认 ChromaDB 集合、Embedding 配置和检索服务连接一致；检查逻辑删除标记 `is_deleted` 是否为有效状态。

### 同一任务被重复处理

检查 Worker 抢占逻辑、任务租约、结果队列幂等消费以及结果表的唯一约束。结果落库应使用幂等策略，例如基于任务标识的唯一约束与 `INSERT IGNORE` / 等价实现。

### 服务间调用返回 401 / 403

检查 `INTERNAL_TOKEN` 与请求头 `X-Internal-Token` 是否一致，确认内部接口未被错误暴露为公网接口。

---

## 11. 开发约定

- 统一由 Gateway 暴露对外 API；服务间调用优先使用内部路由并进行令牌校验。
- 所有消息消费逻辑必须考虑至少一次投递下的重复消费。
- 新增任务状态、进度和事件序号时，须保证前后端、Worker、结果消费逻辑一致。
- 处理失败须保留可读失败原因，便于审核人员与开发人员定位问题。
- 配置变更通过 `km.config.changed` 通知相关服务刷新动态配置，不应依赖重启生效。
- 禁止提交 `.env`、生产密码、对象存储密钥及模型 API Key；请使用本地环境变量、Nacos 或安全配置中心管理敏感信息。

---

## 12. 文档清单

建议与项目代码一并维护：

- 需求说明书
- 概要设计与详细设计文档
- 数据库设计与数据字典
- 接口文档 / Apifox 或 OpenAPI 定义
- RabbitMQ 队列与消息体说明
- 部署手册与环境配置说明
- 测试报告、联调记录与验收清单

---

## 13. 版本说明

本 README 按当前知识管理项目的整合代码结构编写，覆盖网关、`km-admin-service`、`km-search-service`、`km-worker-service`、FastAPI AI 文档处理服务、Vue 前端及相关中间件。

具体端口、配置文件名、部署 profile、镜像标签和前端目录名称以仓库中的 `docker-compose.yml`、Nacos 配置、`application.yml`、`vite.config.*` 与各模块构建文件为最终依据。
