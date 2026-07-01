"""
F4 模块整合后的 km-ai-service 主入口（commit #21）。
- F4 真实实现：杨家凤提交的 parse / chunk / process-document（注册为 process_router）
- F5 占位：embed / vector delete 仍为 Mock，由黄依诺后续合并
- 健康检查：/health 公开（容器探活无需 Token）
- 内部接口：/internal/ai/* 强制 X-Internal-Token（commit #22 加固）
"""
from fastapi import FastAPI

from app.api.process import router as process_router
from app.api.retrieval import router as retrieval_router

app = FastAPI(title="KM AI Service", version="1.1.0")


@app.get("/health")
def health():
    return {"status": "UP", "service": "km-ai-service", "module": "parse-ocr-chunk"}


# F4：杨家凤（commit #21 合并，#22 加固）
app.include_router(process_router)
app.include_router(retrieval_router)
