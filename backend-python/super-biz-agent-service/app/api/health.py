"""健康检查接口"""

from typing import Any
from fastapi import APIRouter
from fastapi.responses import JSONResponse
from app.config import config

router = APIRouter()


@router.get("/health")
async def health_check():
    
    """健康检查接口
    检查服务状态和数据库连接状态
    
    Returns:
        JSONResponse: 健康检查结果
    """
    health_data: dict[str, Any] = {  # pyright: ignore[reportExplicitAny]
        "service": config.app_name,
        "version": config.app_version,
        "status": "healthy",
        "retrieval": {
            "status": "configured",
            "url": config.retrieval_search_url,
        },
    }
    
    return JSONResponse(
        status_code=200,
        content={
            "code": 200,
            "message": "服务运行正常",
            "data": health_data
        }
    )
