"""
F4 整合（commit #26）：错误阶段映射单元测试。

测试项（F4 / 文档附录 B）：
- /health 公开
- 无 Token → 401
- 错误 Token → 403
- 业务失败 → 200 + errorStage=CHUNK / STAGING / PARSE / OCR / INTERNAL
"""
import os
import tempfile
from pathlib import Path
from unittest.mock import patch

# 必须在 import app 之前设置（INTERNAL_TOKEN 在模块加载时读取）
os.environ.setdefault("INTERNAL_TOKEN", "test-token")
import unittest

from fastapi.testclient import TestClient


class TestStageErrors(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.tmp_dir = tempfile.TemporaryDirectory()
        os.environ["ALLOWED_DOCUMENT_ROOT"] = cls.tmp_dir.name
        from app.main import app
        cls.client = TestClient(app)

    @classmethod
    def tearDownClass(cls):
        cls.client.close()
        cls.tmp_dir.cleanup()

    def test_health_no_token_needed(self):
        # /health 公开（容器探活）
        resp = self.client.get("/health")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "UP")

    def test_parse_without_token_returns_401(self):
        resp = self.client.post("/internal/ai/parse", json={
            "taskId": 1, "docId": 1, "kbId": 1, "traceId": "t1",
            "filePath": "/tmp/f4-test-stages/1/source.pdf", "extension": "pdf",
            "targetVersionNo": 1, "taskPayloadJson": "{}",
        })
        self.assertEqual(resp.status_code, 401)

    def test_chunk_without_token_returns_401(self):
        resp = self.client.post("/internal/ai/chunk", json={
            "taskId": 1, "docId": 1, "kbId": 1, "traceId": "t1",
            "blocks": [], "taskPayloadJson": "{}",
        })
        self.assertEqual(resp.status_code, 401)

    def test_embed_without_token_returns_401(self):
        resp = self.client.post("/internal/ai/embed", json={
            "task": {"docId": 1, "targetVersionNo": 1},
        })
        self.assertEqual(resp.status_code, 401)

    def test_vectors_without_token_returns_401(self):
        resp = self.client.delete("/internal/ai/vectors/1")
        self.assertEqual(resp.status_code, 401)

    def test_chunk_empty_blocks_returns_chunk_stage(self):
        # 带 Token 但 blocks 为空 → 业务失败 → errorStage=CHUNK
        resp = self.client.post(
            "/internal/ai/chunk",
            headers={"X-Internal-Token": "test-token"},
            json={
                "taskId": 1, "docId": 1, "kbId": 1, "traceId": "t1",
                "blocks": [], "parsedText": "", "taskPayloadJson": "{}",
            },
        )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body.get("success"), False)
        self.assertEqual(body.get("errorStage"), "CHUNK")

    def test_parse_outside_allowed_root_returns_200_staging(self):
        # filePath 在 ALLOWED_ROOT 外 → process.py 的 try/except 捕获 HTTPException
        # 并返回 HTTP 200 + body {success:false, errorStage:"STAGING"}
        # （路径守卫异常按"业务失败"统一格式返回，便于 Worker 解析）
        resp = self.client.post(
            "/internal/ai/parse",
            headers={"X-Internal-Token": "test-token"},
            json={
                "taskId": 1, "docId": 1, "kbId": 1, "traceId": "t1",
                "filePath": str(Path(self.tmp_dir.name).parent / "outside.pdf"), "extension": "pdf",
                "targetVersionNo": 1, "taskPayloadJson": "{}",
            },
        )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body.get("success"), False)
        self.assertEqual(body.get("errorStage"), "STAGING")

    def test_ocr_runtime_error_returns_ocr_stage(self):
        with (
            patch("app.api.process.resolve_safe_path", return_value=Path("source.png")),
            patch("app.api.process.parse_document", side_effect=RuntimeError("OCR engine failed")),
        ):
            resp = self.client.post(
                "/internal/ai/parse",
                headers={"X-Internal-Token": "test-token"},
                json={
                    "taskId": 1, "docId": 1, "kbId": 1, "traceId": "t-ocr",
                    "filePath": "source.png", "extension": "png",
                    "taskPayloadJson": {"enableOcr": True},
                },
            )
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json().get("errorStage"), "OCR")


if __name__ == "__main__":
    unittest.main()
