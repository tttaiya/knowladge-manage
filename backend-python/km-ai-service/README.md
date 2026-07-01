# km-ai-service：F4 文档解析、OCR 与文本切片

该服务由 Java Worker 在内网调用，负责真实文件解析、扫描件 OCR 和文本切片。Worker 先从 MinIO 将对象下载到共享的 `task-files` 卷，FastAPI 只读取 `ALLOWED_DOCUMENT_ROOT` 下的暂存文件；任务结束后由 Worker 在 `finally` 中清理。

## 已支持能力

| 优先级 | 格式 | 实现 |
|---|---|---|
| P0 | PDF | PyMuPDF 文本层；文本不足时可转 PaddleOCR |
| P0 | DOCX | python-docx，保留标题路径并提取表格 |
| P0 | TXT / Markdown | UTF-8、GBK、GB18030 编码探测 |
| P0 | PNG / JPG / BMP / WebP | PaddleOCR |
| P1 | PPTX | python-pptx |
| P1 | XLSX | openpyxl |

切片支持 `fixed` 与 `heading` 两种模式。每个 chunk 返回 `content`、`chunkIndex`、`pageNo`、`chapterPath`、`chunkType` 和 `charCount`。

## Docker 启动

本服务的 Docker build context 是仓库根目录：

```powershell
cd D:\Download\idea\work\knowladge-manage
docker compose -f infra/docker/docker-compose.demo.yml up -d --build fastapi-ai-service
docker compose -f infra/docker/docker-compose.demo.yml exec fastapi-ai-service `
  python -c "import urllib.request; print(urllib.request.urlopen('http://127.0.0.1:8000/health').read().decode())"
```

容器内端口为 `8000`，正式 Worker 地址为 `http://fastapi-ai-service:8000`。Nginx 不对公网暴露 `/internal/ai/*`。

## 环境变量

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `INTERNAL_TOKEN` | 无 | 必填；对应请求头 `X-Internal-Token` |
| `ALLOWED_DOCUMENT_ROOT` | `/data/task-files` | FastAPI 可读取的唯一文件根目录 |
| `MAX_FILE_SIZE_MB` | `50` | 单文件大小上限 |
| `MAX_PDF_PAGES` | `200` | PDF 服务端硬上限；请求只能进一步降低 |
| `OCR_CPU_THREADS` | `2` | OCR CPU 推理线程数；容器默认关闭 MKL-DNN/IR 优化 |

未配置 `INTERNAL_TOKEN` 时内部接口 fail-closed，返回 HTTP 503。

## 本地开发与测试

要求 Python 3.10：

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
$env:INTERNAL_TOKEN = "test-token"
$env:ALLOWED_DOCUMENT_ROOT = (Resolve-Path test-data\documents)
.\.venv\Scripts\python.exe -m pytest -q
```

需要真实 OCR 时再安装：

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-ocr.txt
```

完整请求、响应与错误约定见 [km-ai-service-API-document.md](km-ai-service-API-document.md)。

## 模块边界

- F4 维护：`parse`、`chunk`、`process-document`、解析依赖、OCR 和资源限制。
- Worker 维护：MinIO 下载、调用超时/重试、暂存目录清理。
- F5 维护：`embed` 与向量删除。当前 F4 改动不修改 F5 公共契约。
