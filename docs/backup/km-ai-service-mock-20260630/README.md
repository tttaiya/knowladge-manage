# Mock km-ai-service 备份（2026-06-30 F4 整合前）

**重要提示**：本次备份是 F4（杨家凤提交的 km-ai-service）整合到主项目之前的状态，**仅用于回退**，不要直接恢复。

## 备份内容

- `main.py`：原始 73 行 Mock（仅含 /health + /parse + /chunk + /embed + /vectors/{doc_id} + /vectors/{doc_id}/versions/{version_no}）
- `Dockerfile`：原始 Mock 版 Dockerfile（只装 fastapi/uvicorn）
- `requirements.txt`：原始依赖（仅 fastapi==0.110.0 / uvicorn[standard]==0.27.1）

## F4 整合后的差异

| 项 | Mock（备份） | F4 整合后 |
|---|---|---|
| parse | 硬编码返回"课程设计联调解析文本" | 杨家凤真实解析（PyMuPDF/docx/pptx/xlsx/txt/md + PaddleOCR） |
| chunk | 硬编码返回 1 个 chunk | 杨家凤 fixed/heading 切片 |
| embed | Mock | 仍是 Mock（F5 黄依诺接手后替换） |
| vectors | Mock | 仍是 Mock（同上） |
| 内部 Token | 无校验 | /internal/ai/* 强制 X-Internal-Token |
| 受限目录 | 无校验 | ALLOWED_DOCUMENT_ROOT + 路径守卫 |
| 错误阶段 | 全部 PARSE_OR_OCR | PARSE/OCR/CHUNK/INTERNAL |
| OCR | 无 | PaddleOCR（需装 paddleocr/paddlepaddle） |
| 文件读取 | 不读文件 | 接收 filePath（worker 暂存） |

## 回退步骤

如需回退到 Mock 状态：

```bash
# 1. 备份当前 F4 状态
cp -r backend-python/km-ai-service backend-python/km-ai-service.f4.bak

# 2. 恢复 Mock
cp docs/backup/km-ai-service-mock-20260630/main.py backend-python/km-ai-service/app/main.py
cp docs/backup/km-ai-service-mock-20260630/Dockerfile backend-python/km-ai-service/Dockerfile
cp docs/backup/km-ai-service-mock-20260630/requirements.txt backend-python/km-ai-service/requirements.txt

# 3. Worker 改造（commit #23/24）需要回退，否则会调用 /parse 时路径为空报错
git revert <commit-id-for-#23> <commit-id-for-#24>
```

**不要**单独回退 F4 代码而不回退 Worker 改造 —— Worker FastApiClient 现在调 `/internal/ai/parse` 传 stagedFilePath，Mock 不读文件会失败。