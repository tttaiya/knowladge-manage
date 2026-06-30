# km-ai-service：知识管理组 F4 文档解析 / OCR / 切片服务

> **API 交接文档**（给组长 / Java 端）：见项目根目录 `km-ai-service-API文档.md`

## 快速启动（本地开发）

```powershell
# 1. 安装依赖
pip install -r requirements.txt -r requirements-ocr.txt

# 2. 启动
.venv310\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8004

# 或者直接双击 run.bat
```

## 目录结构

```
app/
├── main.py                  # FastAPI 入口
├── api/process.py           # 所有接口
├── schemas/
│   ├── common.py            # 基类 + TaskPayload
│   └── process.py           # 请求/响应模型
└── services/
    ├── parsers/             # PDF/DOCX/TXT/PPTX/XLSX/图片 解析
    ├── ocr/                 # PaddleOCR 引擎
    └── chunkers/            # fixed / heading 切片
```

## 接口概览

| 方法 | 路径 | 用途 |
|------|------|------|
| `GET` | `/health` | 健康检查 |
| `POST` | `/internal/ai/parse` | 文档解析 |
| `POST` | `/internal/ai/chunk` | 文本切片 |
| `POST` | `/internal/ai/process-document` | parse + chunk 一步完成（仅调试） |

完整请求/返回 JSON 见 **`km-ai-service-API文档.md`**。
