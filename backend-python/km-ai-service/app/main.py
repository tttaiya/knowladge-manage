"""
F4 模块整合后的 km-ai-service 主入口（commit #21）。
- F4 真实实现：杨家凤提交的 parse / chunk / process-document（注册为 process_router）
- F5 占位：embed / vector delete 仍为 Mock，由黄依诺后续合并
- 健康检查：/health 公开（容器探活无需 Token）
- 内部接口：/internal/ai/* 强制 X-Internal-Token（commit #22 加固）
"""
from fastapi import Depends, FastAPI

from app.api.process import router as process_router
from app.middleware import require_internal_token

app = FastAPI(title="KM AI Service", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "UP", "service": "km-ai-service", "module": "parse-ocr-chunk"}


# F4：杨家凤（commit #21 合并，#22 加固）
app.include_router(process_router)


# F5：黄依诺负责，本期保留 Mock（Worker 联调过渡）；同样要求 Token 校验（R-F4-1）
@app.post("/internal/ai/embed", dependencies=[Depends(require_internal_token)])
def embed(payload: dict):
    """TODO(F5): 黄依诺合并 embed/vector 实现。本次保留 Mock 以便 Worker 联调。"""
    task = payload.get("task", payload)
    doc_id = task.get("docId")
    target_version = task.get("targetVersionNo") or 1
    vector_id = f"vec-{doc_id}-{target_version}-1"
    return {
        "docId": doc_id,
        "vectorIds": [vector_id],
        "chunks": [{
            "docId": doc_id,
            "versionNo": target_version,
            "chunkIndex": 1,
            "content": "F4 课程设计联调解析文本",
            "vectorId": vector_id,
        }],
    }


@app.delete("/internal/ai/vectors/{doc_id}", dependencies=[Depends(require_internal_token)])
def delete_vectors(doc_id: int):
    """TODO(F5): 黄依诺合并。"""
    return {"docId": doc_id, "deleted": True}


@app.delete(
    "/internal/ai/vectors/{doc_id}/versions/{version_no}",
    dependencies=[Depends(require_internal_token)],
)
def delete_vectors_version(doc_id: int, version_no: int):
    """TODO(F5): 黄依诺合并。"""
    return {"docId": doc_id, "versionNo": version_no, "deleted": True}