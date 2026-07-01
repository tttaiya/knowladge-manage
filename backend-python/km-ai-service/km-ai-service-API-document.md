# km-ai-service F4 内部 API 契约

- 契约版本：`1.1.0`
- 更新日期：`2026-07-01`
- 调用方：`km-worker-service`
- 容器内 Base URL：`http://fastapi-ai-service:8000`

## 1. 通用约定

除 `/health` 外，所有 `/internal/ai/*` 请求必须携带：

```http
Content-Type: application/json
X-Internal-Token: <与服务端 INTERNAL_TOKEN 一致>
```

鉴权失败分别返回 HTTP 401（缺少 Token）、403（Token 错误）、503（服务端未配置 Token）。请求模型校验失败返回 HTTP 422。进入业务处理后的失败保持 Worker 兼容约定：HTTP 200，响应体 `success=false`，并携带 `errorStage` 与 `errorMessage`。

正式流程：Worker 从 MinIO 下载对象到 `/data/task-files/{taskId}/source.{extension}`，调用 `/parse`，再将真实 `blocks` 原样传给 `/chunk`；任务结束后由 Worker 在 `finally` 中清理暂存目录。MinIO objectKey 不能直接作为 `filePath`。

## 2. 健康检查

`GET /health` 无需 Token：

```json
{"status":"UP","service":"km-ai-service","module":"parse-ocr-chunk"}
```

## 3. 文档解析

### `POST /internal/ai/parse`

```json
{
  "taskId": 101,
  "docId": 202,
  "kbId": 3,
  "taskType": "PROCESS",
  "traceId": "trace-20260701-001",
  "filePath": "/data/task-files/101/source.pdf",
  "extension": "pdf",
  "strategyVersion": 1,
  "targetVersionNo": 1,
  "taskPayloadJson": {
    "parseBackend": "pymupdf",
    "chunkMode": "fixed",
    "chunkSize": 500,
    "overlap": 50,
    "separator": ["\n\n", "\n", "。"],
    "enableOcr": true,
    "minPdfTextChars": 30,
    "maxPdfPages": 200
  }
}
```

`filePath` 必须位于 `ALLOWED_DOCUMENT_ROOT` 下，且请求扩展名与真实后缀一致。`taskPayloadJson` 可传对象或 JSON 字符串。

| 配置字段 | 默认值 | 约束 |
|---|---:|---|
| `parseBackend` | `pymupdf` | `pymupdf` / `self` / `auto` / `paddleocr` |
| `chunkMode` | `fixed` | `fixed` / `heading` |
| `chunkSize` | 500 | 自动限制到 50–5000，与知识库配置约束一致 |
| `overlap` | 50 | 自动限制到 0–1000；实际不得大于等于 chunkSize |
| `separator` | `\n\n` | 字符串或字符串数组 |
| `enableOcr` | true | 图片必须为 true |
| `minPdfTextChars` | 30 | PDF 文本层少于该值时尝试 OCR |
| `maxPdfPages` | 200 | 必须大于 0，且不得超过服务端硬上限 |

兼容 F2 已落地的重处理消息：当顶层未提供切片字段时，F4 会从 `knowledgeBaseSnapshot.chunkStrategy/chunkSize/chunkOverlap/separators` 读取；顶层 F4 字段优先。

成功响应：

```json
{
  "success": true,
  "taskId": 101,
  "docId": 202,
  "kbId": 3,
  "traceId": "trace-20260701-001",
  "extension": "pdf",
  "parsedText": "第一章 项目概述\n\n正文……",
  "blocks": [{
    "content": "第一章 项目概述\n正文……",
    "pageNo": 1,
    "blockType": "paragraph",
    "chapterPath": "第一章 项目概述",
    "charCount": 18
  }]
}
```

`pageNo` 与 `chapterPath` 无法确定时省略；`blockType` 为 `paragraph`、`table` 或 `ocr`。

## 4. 文本切片

### `POST /internal/ai/chunk`

请求优先使用 `/parse` 返回的 `blocks`；仅本地调试时可只传 `parsedText`。

```json
{
  "taskId": 101,
  "docId": 202,
  "kbId": 3,
  "traceId": "trace-20260701-001",
  "blocks": [{
    "content": "第一章 项目概述\n正文……",
    "pageNo": 1,
    "blockType": "paragraph",
    "chapterPath": "第一章 项目概述",
    "charCount": 18
  }],
  "taskPayloadJson": {
    "chunkMode": "heading",
    "chunkSize": 500,
    "overlap": 50,
    "separator": ["\n\n", "\n", "。"]
  }
}
```

成功响应：

```json
{
  "success": true,
  "taskId": 101,
  "docId": 202,
  "kbId": 3,
  "traceId": "trace-20260701-001",
  "chunks": [{
    "content": "第一章 项目概述\n正文……",
    "chapterPath": "第一章 项目概述",
    "pageNo": 1,
    "chunkType": "paragraph",
    "charCount": 18,
    "chunkIndex": 1
  }],
  "chunkCount": 1
}
```

`chunkIndex` 从 1 开始，表示数据库及向量元数据中的顺序；`charCount` 等于 `content` 字符数。

## 5. 错误阶段

| `errorStage` | 含义 | 示例 |
|---|---|---|
| `STAGING` | 文件访问/输入边界 | 路径越界、文件不存在、空文件、超大小、后缀不匹配 |
| `PARSE` | 文档解析 | 格式不支持、文件损坏、空解析结果、PDF 超页数 |
| `OCR` | OCR | PaddleOCR 未安装、模型加载或识别失败 |
| `CHUNK` | 文本切片 | 未传 blocks/parsedText 或切片执行失败 |
| `INTERNAL` | 未分类内部错误 | 非预期异常，需结合 traceId 查日志 |

Worker 必须按 `success` 判断结果并按 `errorStage` 上报，不能只判断 HTTP 200。错误信息最多返回 500 字符。

## 6. 格式、限制与兼容接口

P0：PDF、DOCX、TXT、MD、PNG、JPG、JPEG、BMP、WebP。P1：PPTX、XLSX。服务端默认限制单文件 50MB、PDF 200 页；请求只能进一步降低 PDF 页数限制。

`POST /internal/ai/process-document` 仅用于本地调试。正式 Worker 必须分步调用 `/parse` 与 `/chunk`，以正确记录任务阶段。

## 7. 版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| 1.1.0 | 2026-07-01 | 对齐端口与 Token；补全依赖、页数限制、错误阶段和 chunk 元数据 |
| 1.0.0 | 2026-06-30 | 初始 parse/chunk 契约 |
