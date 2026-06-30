"""
F4 整合（commit #26）：内部 Token 校验单元测试。

测试项（F4-02）：
- 缺失 X-Internal-Token → 401
- 错误 X-Internal-Token → 403
- 正确 X-Internal-Token → 200（调用依赖返回 token 值）
- INTERNAL_TOKEN 未配置 → 503
"""
import os
import unittest
from unittest.mock import AsyncMock

from fastapi import HTTPException

os.environ.setdefault("INTERNAL_TOKEN", "test-internal-token-2026")


class TestInternalToken(unittest.IsolatedAsyncioTestCase):

    async def test_missing_token_returns_401(self):
        # 重新 import 让 monkey-patched 环境变量生效
        import importlib
        import app.middleware as mw
        importlib.reload(mw)
        with self.assertRaises(HTTPException) as ctx:
            await mw.require_internal_token(x_internal_token="")
        self.assertEqual(ctx.exception.status_code, 401)
        self.assertIn("Missing", str(ctx.exception.detail))

    async def test_wrong_token_returns_403(self):
        import importlib
        import app.middleware as mw
        importlib.reload(mw)
        with self.assertRaises(HTTPException) as ctx:
            await mw.require_internal_token(x_internal_token="wrong-token")
        self.assertEqual(ctx.exception.status_code, 403)
        self.assertIn("Invalid", str(ctx.exception.detail))

    async def test_correct_token_passes(self):
        import importlib
        import app.middleware as mw
        importlib.reload(mw)
        result = await mw.require_internal_token(x_internal_token="test-internal-token-2026")
        self.assertEqual(result, "test-internal-token-2026")

    async def test_unconfigured_token_returns_503(self):
        # 临时清空环境变量
        original = os.environ.pop("INTERNAL_TOKEN", None)
        try:
            import importlib
            import app.middleware as mw
            importlib.reload(mw)
            with self.assertRaises(HTTPException) as ctx:
                await mw.require_internal_token(x_internal_token="anything")
            self.assertEqual(ctx.exception.status_code, 503)
        finally:
            if original is not None:
                os.environ["INTERNAL_TOKEN"] = original


if __name__ == "__main__":
    unittest.main()