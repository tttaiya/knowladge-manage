# SuperBizAgent

企业级智能问答与 OnCall 辅助系统，基于 FastAPI + Milvus + DashScope，支持知识库问答、流式对话、引用溯源、会话历史和基础运营管理。

## 当前状态

- 已支持真实 `Milvus` 向量写入与向量召回
- 已支持知识库维度过滤、引用详情弹窗、原文下载
- 已支持服务端会话持久化与历史消息“思考过程”回放
- `Milvus` 不可用时会自动降级到数据库检索兜底

## 核心能力

- 智能对话：普通对话、流式对话、服务端会话历史
- RAG 知识库：文档上传、切块、Embedding、Milvus 写入、按知识库检索
- 引用溯源：回答内 `[1][2]...` 角标、引用详情、来源文档下载
- 思考过程：前端展示节点状态，切换会话后仍可回放历史过程
- 管理后台：知识库管理、系统设置、基础统计
- Trace / Bad Case / Eval：支持观测、复盘和回归验证

## 技术栈

- 后端：FastAPI
- LLM / Embedding：阿里云 DashScope / 通义系列
- 向量库：Milvus
- 前端：原生 HTML / CSS / JavaScript
- 数据库：SQLite
- 协议：SSE、MCP

## 目录结构

```text
super_biz_agent_py/
├─ app/                    # 后端应用代码
│  ├─ api/                 # HTTP API
│  ├─ auth/                # 认证鉴权
│  ├─ core/                # Milvus / LLM 等核心组件
│  ├─ db/                  # 数据库初始化
│  ├─ models/              # ORM / Schema / 请求响应模型
│  ├─ repositories/        # 数据访问层
│  ├─ services/            # 业务服务与 RAG 流水线
│  ├─ tools/               # Agent 工具
│  └─ utils/               # 日志等公共工具
├─ static/                 # 前端页面与脚本
├─ tests/                  # 自动化测试
├─ docs/                   # 项目文档
│  ├─ design/              # 方案、设计、实施提示
│  └─ tasks/               # 模块级任务拆分
├─ scripts/
│  └─ windows/             # Windows 启停脚本实际实现
├─ mcp_servers/            # MCP 服务
├─ knowledge-docs/         # 示例知识文档
├─ prometheus/             # Prometheus 配置
├─ uploads/                # 上传文件与知识库存档
├─ badcases/               # Bad Case 运行产物
├─ traces/                 # Trace 运行产物
├─ volumes/                # Docker / Milvus 持久化数据
├─ start-windows.bat       # Windows 启动入口（兼容保留）
├─ stop-windows.bat        # Windows 停止入口（兼容保留）
├─ vector-database.yml     # Milvus / Attu / MinIO / Etcd 编排
├─ pyproject.toml          # Python 项目配置
└─ README.md
```

## 关键文档

- 产品与改造背景：[docs/design/proposal.md](docs/design/proposal.md)
- 详细设计：[docs/design/detailed-design.md](docs/design/detailed-design.md)
- 实施提示：[docs/design/implementation-prompt.md](docs/design/implementation-prompt.md)
- 任务看板：[docs/tasks/progress.md](docs/tasks/progress.md)
- Demo 清单：[docs/demo_checklist.md](docs/demo_checklist.md)
- 工具设计：[docs/tool_system_design.md](docs/tool_system_design.md)

## 环境要求

- Python `3.11` - `3.13`
- Docker Desktop
- DashScope API Key

## 配置说明

项目通过根目录 `.env` 配置运行参数，常用项如下：

```env
# LLM / Embedding
DASHSCOPE_API_KEY=your-api-key
DASHSCOPE_API_BASE=https://dashscope.aliyuncs.com/compatible-mode/v1
DASHSCOPE_MODEL=qwen-max
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v4

# Milvus
MILVUS_HOST=localhost
MILVUS_PORT=19530

# App
DATABASE_URL=sqlite:///./super_biz_agent.db
AUTH_SECRET_KEY=change-me
```

## 快速开始

### 1. 安装依赖

Windows PowerShell：

```powershell
uv venv
.\.venv\Scripts\activate
uv pip install -e .
```

或使用 `pip`：

```powershell
python -m venv .venv
.\.venv\Scripts\activate
pip install -e .
```

### 2. 启动 Milvus

```powershell
docker compose -f vector-database.yml up -d
```

启动后可访问：

- Milvus: `localhost:19530`
- Attu: [http://localhost:8000](http://localhost:8000)
- MinIO: [http://localhost:9001](http://localhost:9001)

### 3. 启动项目

Windows 下推荐直接使用兼容入口：

```powershell
.\start-windows.bat
```

它会调用实际脚本：

```text
scripts/windows/start-windows.bat
```

手动启动主服务：

```powershell
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 9900
```

### 4. 访问地址

- 首页：[http://127.0.0.1:9900](http://127.0.0.1:9900)
- 管理后台：[http://127.0.0.1:9900/static/admin.html](http://127.0.0.1:9900/static/admin.html)
- Swagger：[http://127.0.0.1:9900/docs](http://127.0.0.1:9900/docs)
- 健康检查：[http://127.0.0.1:9900/health](http://127.0.0.1:9900/health)

## RAG 主链路

```text
文档上传
→ 文本切块
→ 生成 chunk embedding
→ 写入 Milvus collection `biz`
→ 提问时生成 query embedding
→ Milvus 检索
→ 可选 rerank / DB fallback
→ 大模型生成答案
→ 返回引用
```

当前实现要点：

- `knowledge_base_id`、`document_id`、`chunk_id` 会写入 Milvus 标量字段
- 回答优先走 Milvus
- Milvus 异常或无结果时，会自动 fallback 到数据库检索

## 常用接口

### 聊天

- `POST /api/chat`
- `POST /api/chat_stream`
- `GET /api/chat/session/{session_id}`

示例：

```bash
curl -X POST "http://127.0.0.1:9900/api/chat" \
  -H "Content-Type: application/json" \
  -d '{
    "Question": "电力安全工器具有哪些？",
    "Id": "session-demo",
    "mode": "knowledge"
  }'
```

### 知识库管理

- `POST /api/admin/knowledge-bases`
- `GET /api/admin/knowledge-bases`
- `POST /api/admin/knowledge-bases/{kb_id}/documents`
- `GET /api/admin/knowledge-bases/{kb_id}/documents`
- `DELETE /api/admin/knowledge-bases/{kb_id}`

### 引用详情

- `GET /api/messages/{message_id}/citations`

## 测试

运行核心接口测试：

```powershell
.\.venv\Scripts\python.exe -m pytest tests\test_productized_api.py -q
```

运行全部测试：

```powershell
.\.venv\Scripts\python.exe -m pytest
```

## Windows 启停说明

根目录保留了兼容入口：

```powershell
.\start-windows.bat
.\stop-windows.bat
```

实际脚本位置：

```text
scripts/windows/start-windows.bat
scripts/windows/stop-windows.bat
```

## 运行产物说明

这些目录和文件是运行中会产生的内容，不建议手工提交：

- `logs/`
- `uploads/`
- `badcases/`
- `traces/`
- `htmlcov/`
- `super_biz_agent.db`
- `server.log` / `server.pid`
- `mcp_cls.log` / `mcp_monitor.log`

## 常见问题

### 1. Milvus 连接失败

先确认 Docker 已启动：

```powershell
docker ps
docker compose -f vector-database.yml ps
```

再检查健康状态：

```powershell
Invoke-RestMethod http://127.0.0.1:9900/health
```

### 2. 上传知识库后检索不到

排查顺序：

1. 确认 `/health` 中 `milvus.status` 为 `connected`
2. 确认文档状态是 `indexed`
3. 必要时重建索引

### 3. Windows 下脚本无法执行

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process
```

### 4. 端口冲突

```powershell
netstat -ano | findstr :9900
netstat -ano | findstr :19530
taskkill /F /PID <PID>
```

## 当前整理说明

这次目录整理采用的是低风险方案：

- 不改 `app` 包导入路径
- 不改 `uvicorn app.main:app` 启动方式
- 只把脚本和设计文档归位到更清晰的目录
- 根目录保留兼容入口，避免影响现有使用习惯

## License

MIT
