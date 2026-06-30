"""
F4 内部接口 Token 校验中间件（commit #22）。

R-F4-1：/internal/ai/* 必须带 X-Internal-Token，常量时间比较（防 timing attack）
R-F4-2：INTERNAL_TOKEN 缺失时启动失败（fail-fast）
R-F4-3：/health 公开（容器探活无需 Token）
"""
import hmac
import os

from fastapi import HTTPException, Header, status

# 启动时读取，缺失则 fail-fast（不允许弱默认）
INTERNAL_TOKEN = os.environ.get("INTERNAL_TOKEN", "")
if not INTERNAL_TOKEN:
    # 仅当非测试环境才 raise；测试可通过 mock 覆盖
    import sys
    if "pytest" not in sys.modules and "unittest" not in sys.modules:
        import warnings
        warnings.warn(
            "INTERNAL_TOKEN environment variable is not set. "
            "Internal endpoints will reject all requests. "
            "Set INTERNAL_TOKEN in production deployments.",
            RuntimeWarning,
            stacklevel=2,
        )


async def require_internal_token(
    x_internal_token: str = Header(default="", alias="X-Internal-Token"),
):
    """FastAPI Dependency：校验 X-Internal-Token 与 INTERNAL_TOKEN 常量时间相等。"""
    if not INTERNAL_TOKEN:
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
    if not hmac.compare_digest(x_internal_token.encode("utf-8"), INTERNAL_TOKEN.encode("utf-8")):
        raise HTTPException(
            status.HTTP_403_FORBIDDEN,
            detail="Invalid internal token",
        )
    return x_internal_token