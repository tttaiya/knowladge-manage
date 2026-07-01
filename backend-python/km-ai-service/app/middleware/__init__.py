"""
F4 内部接口 Token 校验中间件（commit #22）。

R-F4-1：/internal/ai/* 必须带 X-Internal-Token，常量时间比较（防 timing attack）
R-F4-2：INTERNAL_TOKEN 缺失时内部接口 fail-closed（503）
R-F4-3：/health 公开（容器探活无需 Token）
"""
import hmac
import os

from fastapi import HTTPException, Header, status

def get_internal_token() -> str:
    """读取当前内部令牌。

    令牌不做模块级缓存，便于测试隔离和容器滚动配置；一次环境变量读取相对
    文档解析/OCR 的开销可以忽略。未配置时保持 fail-closed，内部接口返回 503。
    """
    return os.environ.get("INTERNAL_TOKEN", "")


async def require_internal_token(
    x_internal_token: str = Header(default="", alias="X-Internal-Token"),
):
    """FastAPI Dependency：校验 X-Internal-Token 与 INTERNAL_TOKEN 常量时间相等。"""
    internal_token = get_internal_token()
    if not internal_token:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="INTERNAL_TOKEN not configured on server",
        )
    if not x_internal_token:
        raise HTTPException(
            status.HTTP_401_UNAUTHORIZED,
            detail="Missing X-Internal-Token header",
        )
    # hmac.compare_digest 防止 timing attack
    if not hmac.compare_digest(x_internal_token.encode("utf-8"), internal_token.encode("utf-8")):
        raise HTTPException(
            status.HTTP_403_FORBIDDEN,
            detail="Invalid internal token",
        )
    return x_internal_token
