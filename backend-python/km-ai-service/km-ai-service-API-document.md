# km-ai-service API 交接文档

> **服务说明**: 知识管理组 F4 — 文档解析 / PaddleOCR / 文本切片微服务
>
> **提供给**: Java Worker 端直接调用
>
> **版本**: 1.0.0

---

## 一、启动方式

### 方式一：Docker 启动（⭐ 推荐，无需装 Python）

组长拿到项目包后，只需装好 [Docker Desktop](https://www.docker.com/products/docker-desktop/)，然后：

```powershell
# 1. 进入项目目录
cd km-ai-service

# 2. 构建镜像 + 启动容器
docker compose up -d

# 3. 确认服务已就绪
curl http://localhost:8004/health
```

服务地址：`http://<宿主机IP>:8004`

#### ⚠️ 重要：文档路径映射

容器内部只能访问挂载进去的目录。在 `docker-compose.yml` 中已配置默认映射：

```yaml
volumes:
  - ./test-data:/data/docs:ro
```

**组长必须修改**：把 `./test-data` 改成他机器上实际存放文档的路径，比如：

```yaml
# Windows 示例
volumes:
  - D:/shared-docs:/data/docs:ro

# Linux 示例
volumes:
  - /mnt/nas/docs:/data/docs:ro
```

修改后重启：
```powershell
docker compose down && docker compose up -d
```

然后调接口时 `filePath` 填容器内路径，比如 `"/data/docs/sample.pdf"`。

#### 常用命令

| 操作 | 命令 |
|------|------|
| 启动 | `docker compose up -d` |
| 停止 | `docker compose down` |
| 查看日志 | `docker logs -f km-ai-service` |
| 重启 | `docker compose restart` |
| Swagger 文档 | 浏览器打开 `http://localhost:8004/docs` |

---

### 方式二：本地 Python 启动（开发/调试用）

#### 环境要求

| 项目 | 要求 |
|------|------|
| Python | 3.10.x（必须） |
| 虚拟环境 | `.venv310/` |
| 工作目录 | `backend-python/km-ai-service/` |

#### 安装依赖

```bash
cd backend-python/km-ai-service
pip install -r requirements.txt
pip install -r requirements-ocr.txt
```

#### 启动命令

```bash
cd backend-python/km-ai-service
python -m uvicorn app.main:app --host 0.0.0.0 --port 8004
```

| 参数 | 值 | 说明 |
|------|-----|------|
| `--host` | `0.0.0.0` | 监听所有网卡 |
| `--port` | `8004` | 服务端口 |

#### Windows 一键启动

直接双击 `run.bat`。

---

## 二、接口地址

> **Base URL**: `http://<你的服务器IP>:8004`

| 方法 | 路径 | 用途 | Java 调用优先级 |
|------|------|------|:---:|
| `GET` | `/health` | 健康检查 | 启动时探测 |
| `POST` | `/internal/ai/parse` | 解析文档 → 返回结构化 blocks | ⭐ 主接口 |
| `POST` | `/internal/ai/chunk` | 对 blocks 切片 → 返回 chunks | ⭐ 主接口 |
| `POST` | `/internal/ai/process-document` | 解析 + 切片 一步完成 | 仅调试用 |

> **调用流程**: Java Worker 先调 `/internal/ai/parse` 拿到 blocks，再调 `/internal/ai/chunk` 拿到 chunks。

### 注意事项

- **无认证**: 接口没有 API Key / JWT / OAuth，设计为内网部署或在 Nginx/Gateway 后运行。
- **错误处理**: 所有接口 HTTP 状态码正常时都返回 `200`，通过响应体中的 `success` 字段（`true`/`false`）判断成败。失败时附带 `errorStage` 和 `errorMessage`。
- **字段命名**: 请求和响应均使用 **camelCase**（与 Java 保持一致）。

---

## 三、接口详细定义

---

### 3.1 `GET /health` — 健康检查

Java 启动时可先调此接口确认服务已就绪。

**请求**: 无参数，无 Body。

**返回 JSON**:
```json
{
  "status": "UP",
  "service": "km-ai-service",
  "module": "parse-ocr-chunk"
}
```

---

### 3.2 `POST /internal/ai/parse` — 文档解析

解析本地文件系统中的文档，提取文本并返回结构化 blocks。支持 PDF、DOCX、TXT、MD、PPTX、XLSX、PNG、JPG、BMP、WebP。

#### 请求 JSON

```json
{
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "taskType": "PROCESS",
  "triggerSource": "USER_UPLOAD",
  "traceId": "trace-uuid-xxxx",
  "filePath": "/data/documents/sample.pdf",
  "extension": "pdf",
  "strategyVersion": 1,
  "targetVersionNo": 1,
  "taskPayloadJson": {
    "parseBackend": "pymupdf",
    "chunkMode": "fixed",
    "chunkSize": 500,
    "overlap": 50,
    "separator": "\n\n",
    "enableOcr": true,
    "minPdfTextChars": 30
  }
}
```

#### 请求字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `taskId` | int | 否 | Worker 任务 ID |
| `docId` | int | **是** | 文档 ID |
| `kbId` | int | **是** | 知识库 ID |
| `taskType` | String | 否 | 任务类型，如 `"PROCESS"` |
| `triggerSource` | String | 否 | 触发来源，如 `"USER_UPLOAD"` |
| `traceId` | String | 否 | 链路追踪 ID |
| `filePath` | String | **是** | 文件路径（Docker 环境填**容器内路径**如 `/data/docs/xxx.pdf`；本地启动填本机绝对路径） |
| `extension` | String | 否 | 文件扩展名（不传则从路径推断） |
| `strategyVersion` | int | 否 | 策略版本 |
| `targetVersionNo` | int | 否 | 目标版本号 |
| `taskPayloadJson` | Object | 否 | 解析 & 切片配置（见下表） |

#### `taskPayloadJson` 字段说明

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `parseBackend` | String | `"pymupdf"` | 解析后端，可选: `pymupdf`, `self`, `auto`, `paddleocr` |
| `chunkMode` | String | `"fixed"` | 切片模式: `fixed` 固定大小 / `heading` 按标题 |
| `chunkSize` | int | `500` | 切片大小（字符数），范围 [100, 5000] |
| `overlap` | int | `50` | 切片重叠字符数，范围 [0, 1000] |
| `separator` | String / Array | `"\n\n"` | 分隔符，切片时优先在此处断开；也支持字符串列表如 `["\n\n", "\n", "。"]` |
| `enableOcr` | boolean | `true` | PDF 文本层不足时是否启用 OCR |
| `minPdfTextChars` | int | `30` | PDF 文本层低于此字符数则自动切换 OCR |

#### 成功返回 JSON

```json
{
  "success": true,
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "extension": "pdf",
  "parsedText": "第一章 概述\n这是第一章内容。\n一、系统目标\n这里是系统目标内容。\n1.1 处理流程\n上传文档后，系统会解析、OCR、切片。",
  "blocks": [
    {
      "content": "第一章 概述\n这是第一章内容。",
      "pageNo": 1,
      "blockType": "paragraph",
      "chapterPath": "第一章 概述",
      "charCount": 16
    },
    {
      "content": "一、系统目标\n这里是系统目标内容。",
      "pageNo": 1,
      "blockType": "paragraph",
      "chapterPath": "第一章 概述/一、系统目标",
      "charCount": 16
    },
    {
      "content": "上传文档后，系统会解析、OCR、切片。",
      "pageNo": 1,
      "blockType": "paragraph",
      "chapterPath": "第一章 概述/一、系统目标/1.1 处理流程",
      "charCount": 18
    }
  ]
}
```

#### 失败返回 JSON（success = false）

```json
{
  "success": false,
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "parsedText": "",
  "blocks": [],
  "errorStage": "PARSE_OR_OCR",
  "errorMessage": "文件不存在: /data/documents/missing.pdf"
}
```

#### 返回字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | `true` 成功 / `false` 失败 |
| `taskId` | int | Worker 任务 ID（请求传了就有） |
| `docId` | int | 文档 ID |
| `kbId` | int | 知识库 ID |
| `traceId` | String | 链路追踪 ID（请求传了就有） |
| `extension` | String | 检测到的文件扩展名（失败时不返回） |
| `parsedText` | String | blocks 中所有 content 以 `\n\n` 拼接的全文 |
| `blocks` | Array | 结构化解析结果 |
| `blocks[].content` | String | 该 block 的文本内容 |
| `blocks[].pageNo` | int | 所在页码（PDF/图片才有值，无值时不返回此字段） |
| `blocks[].blockType` | String | 类型: `paragraph`（段落）/ `ocr`（OCR识别）/ `table`（表格） |
| `blocks[].chapterPath` | String | 章节路径，如 `"第一章/1.1 背景"`（无值时不返回此字段） |
| `blocks[].charCount` | int | 字符数 |
| `errorStage` | String | 仅失败时出现，值为 `"PARSE_OR_OCR"` |
| `errorMessage` | String | 仅失败时出现，人类可读错误信息 |

---

### 3.3 `POST /internal/ai/chunk` — 文本切片

对已解析的 blocks（或纯文本）按配置切片，返回 chunks。

#### 请求 JSON

```json
{
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "parsedText": null,
  "blocks": [
    {
      "content": "第一章 概述\n这是第一章内容。\n一、系统目标\n这里是系统目标内容。\n1.1 处理流程\n上传文档后，系统会解析、OCR、切片。",
      "pageNo": 1,
      "blockType": "paragraph",
      "chapterPath": null,
      "charCount": 68
    }
  ],
  "taskPayloadJson": {
    "chunkMode": "heading",
    "chunkSize": 120,
    "overlap": 20,
    "separator": "\n\n"
  }
}
```

#### 请求字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `taskId` | int | 否 | Worker 任务 ID |
| `docId` | int | **是** | 文档 ID |
| `kbId` | int | **是** | 知识库 ID |
| `traceId` | String | 否 | 链路追踪 ID |
| `parsedText` | String | 否 | 纯文本（与 `blocks` 二选一；都有则优先用 `blocks`） |
| `blocks` | Array | 否 | 从 `/parse` 返回的 blocks |
| `taskPayloadJson` | Object | 否 | 切片配置（字段同上表，主要是 `chunkMode`, `chunkSize`, `overlap`, `separator`） |

#### 成功返回 JSON

```json
{
  "success": true,
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "chunks": [
    {
      "content": "第一章 概述\n这是第一章内容。",
      "chapterPath": "第一章 概述",
      "pageNo": 1,
      "chunkType": "paragraph",
      "charCount": 16,
      "chunkIndex": 1
    },
    {
      "content": "一、系统目标\n这里是系统目标内容。",
      "chapterPath": "第一章 概述/一、系统目标",
      "pageNo": 1,
      "chunkType": "paragraph",
      "charCount": 16,
      "chunkIndex": 2
    },
    {
      "content": "1.1 处理流程\n上传文档后，系统会解析、OCR、切片。",
      "chapterPath": "第一章 概述/一、系统目标/1.1 处理流程",
      "pageNo": 1,
      "chunkType": "paragraph",
      "charCount": 21,
      "chunkIndex": 3
    }
  ],
  "chunkCount": 3
}
```

#### 失败返回 JSON

```json
{
  "success": false,
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "chunks": [],
  "chunkCount": 0,
  "errorStage": "CHUNK",
  "errorMessage": "chunk 接口需要传 blocks 或 parsedText"
}
```

#### 返回字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | `true` 成功 / `false` 失败 |
| `taskId` | int | Worker 任务 ID（请求传了就有） |
| `docId` | int | 文档 ID |
| `kbId` | int | 知识库 ID |
| `traceId` | String | 链路追踪 ID（请求传了就有） |
| `chunks` | Array | 切片结果 |
| `chunks[].content` | String | 切片文本 |
| `chunks[].chapterPath` | String | 章节路径（无值时不返回） |
| `chunks[].pageNo` | int | 来源页码（无值时不返回） |
| `chunks[].chunkType` | String | 类型（继承自 block） |
| `chunks[].charCount` | int | 字符数 |
| `chunks[].chunkIndex` | int | 切片序号（从 1 开始） |
| `chunkCount` | int | 切片总数 |
| `errorStage` | String | 仅失败时出现，值为 `"CHUNK"` |
| `errorMessage` | String | 仅失败时出现，人类可读错误信息 |

---

### 3.4 `POST /internal/ai/process-document` — 解析+切片（组合接口）

> ⚠️ 仅用于本地调试。正式流程请分步调用 `/parse` → `/chunk`。

请求格式与 **`/internal/ai/parse` 完全一致**。

#### 成功返回 JSON

```json
{
  "success": true,
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "extension": "pdf",
  "parsedText": "全文内容...",
  "blocks": [ /* ...同 parse 返回... */ ],
  "chunks": [ /* ...同 chunk 返回... */ ],
  "chunkCount": 5
}
```

#### 失败返回 JSON

```json
{
  "success": false,
  "taskId": 1,
  "docId": 10,
  "kbId": 3,
  "traceId": "trace-uuid-xxxx",
  "parsedText": "",
  "blocks": [],
  "chunks": [],
  "chunkCount": 0,
  "errorStage": "PROCESS_DOCUMENT",
  "errorMessage": "文件不存在或不是普通文件：/data/docs/missing.pdf"
}
```

#### 返回字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | `true` 成功 / `false` 失败 |
| `taskId` | int | Worker 任务 ID |
| `docId` | int | 文档 ID |
| `kbId` | int | 知识库 ID |
| `traceId` | String | 链路追踪 ID |
| `extension` | String | 检测到的文件扩展名（失败时不返回） |
| `parsedText` | String | blocks 全文拼接 |
| `blocks` | Array | 同 `/parse` 返回的 blocks |
| `chunks` | Array | 同 `/chunk` 返回的 chunks |
| `chunkCount` | int | 切片总数 |
| `errorStage` | String | 仅失败时出现，值为 `"PROCESS_DOCUMENT"` |
| `errorMessage` | String | 仅失败时出现 |

---

## 四、Java 调用示例（伪代码）

```java
// 1. 健康检查
GET http://10.0.0.100:8004/health
// → { "status": "UP", "service": "km-ai-service", "module": "parse-ocr-chunk" }

// 2. 解析文档
POST http://10.0.0.100:8004/internal/ai/parse
Content-Type: application/json

{
  "taskId": 12345,
  "docId": 678,
  "kbId": 3,
  "traceId": "java-worker-uuid",
  "filePath": "/data/docs/sample.pdf",
  "extension": "pdf",
  "taskPayloadJson": {
    "parseBackend": "pymupdf",
    "enableOcr": true,
    "minPdfTextChars": 30
  }
}

// 3. 切片
POST http://10.0.0.100:8004/internal/ai/chunk
Content-Type: application/json

{
  "taskId": 12345,
  "docId": 678,
  "kbId": 3,
  "traceId": "java-worker-uuid",
  "blocks": <第2步返回的 blocks>,
  "taskPayloadJson": {
    "chunkMode": "fixed",
    "chunkSize": 500,
    "overlap": 50,
    "separator": "\n\n"
  }
}
```

**关键提示**:
- `filePath` 是**容器内路径**（Docker 部署时），必须与 `docker-compose.yml` 中 `volumes` 映射的容器内目录一致。例如映射了 `D:/docs:/data/docs`，那 `filePath` 就填 `"/data/docs/xxx.pdf"`。
- 如果需要 Java Worker 和 Python 服务都能访问文件，建议使用共享存储（NAS / NFS），两端挂载到各自能看到的路径，`filePath` 填 Python 服务侧的路径。
- 所有 JSON 字段名使用 camelCase，Java 的 JSON 库（Jackson/Gson/Fastjson）可直接序列化。
- 错误判断请用 `success` 字段，不要依赖 HTTP 状态码。

---

## 五、支持的文件格式

| 优先级 | 格式 | 解析方式 | 需要 OCR |
|:--:|------|----------|:--:|
| P0 | PDF | PyMuPDF 提取文本层，不足时 OCR | 文本不足时自动 |
| P0 | DOCX | python-docx（含标题层级识别） | 否 |
| P0 | TXT / MD | 直接读取（自动检测编码） | 否 |
| P0 | PNG / JPG / BMP / WebP | PaddleOCR | **是** |
| P1 | PPTX | python-pptx | 否 |
| P1 | XLSX | openpyxl（每个 Sheet 为一个 block，类型 `table`） | 否 |

---

## 六、核心依赖

```
fastapi==0.115.6          # Web 框架
uvicorn[standard]==0.34.0 # ASGI 服务器
pydantic==2.10.4          # 数据校验
PyMuPDF==1.24.14          # PDF 解析
python-docx==1.1.2        # Word 解析
python-pptx==1.0.2        # PPT 解析
openpyxl==3.1.5           # Excel 解析
Pillow==11.1.0            # 图片处理
paddleocr==2.9.1          # OCR（可选）
paddlepaddle==2.6.2       # OCR（可选）
```

---

## 七、联系方式

- **服务名称**: km-ai-service
- **Python 版本**: 3.10.11
- **代码位置**: `backend-python/km-ai-service/`
