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
from unittest.mock import patch

from fastapi import HTTPException

class TestInternalToken(unittest.IsolatedAsyncioTestCase):

    async def test_missing_token_returns_401(self):
        import app.middleware as mw
        with patch.dict(os.environ, {"INTERNAL_TOKEN": "test-internal-token-2026"}):
            with self.assertRaises(HTTPException) as ctx:
                await mw.require_internal_token(x_internal_token="")
        self.assertEqual(ctx.exception.status_code, 401)
        self.assertIn("Missing", str(ctx.exception.detail))

    async def test_wrong_token_returns_403(self):
        import app.middleware as mw
        with patch.dict(os.environ, {"INTERNAL_TOKEN": "test-internal-token-2026"}):
            with self.assertRaises(HTTPException) as ctx:
                await mw.require_internal_token(x_internal_token="wrong-token")
        self.assertEqual(ctx.exception.status_code, 403)
        self.assertIn("Invalid", str(ctx.exception.detail))

    async def test_correct_token_passes(self):
        import app.middleware as mw
        with patch.dict(os.environ, {"INTERNAL_TOKEN": "test-internal-token-2026"}):
            result = await mw.require_internal_token(x_internal_token="test-internal-token-2026")
        self.assertEqual(result, "test-internal-token-2026")

    async def test_unconfigured_token_returns_503(self):
        import app.middleware as mw
        with patch.dict(os.environ, {"INTERNAL_TOKEN": ""}):
            with self.assertRaises(HTTPException) as ctx:
                await mw.require_internal_token(x_internal_token="anything")
            self.assertEqual(ctx.exception.status_code, 503)


if __name__ == "__main__":
    unittest.main()
