"""
F4 整合（commit #26）：受限目录守卫单元测试。

测试项（F4-07）：
- /etc/passwd → 拒绝（403）
- ../ 父目录穿越 → 拒绝（403）
- .exe 扩展名 → 拒绝（400）
- extension 与 filePath 后缀不一致 → 拒绝（400）
- 正常 task-files 路径 → 通过
"""
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from fastapi import HTTPException

# 设置测试环境变量（在 import 之前）
os.environ.setdefault("INTERNAL_TOKEN", "test-token")
os.environ.setdefault("ALLOWED_DOCUMENT_ROOT", "/tmp/f4-test-task-files")


class TestPathGuard(unittest.TestCase):

    def setUp(self):
        # 创建临时 task-files 目录
        self.tmp_root = Path(os.environ["ALLOWED_DOCUMENT_ROOT"])
        self.tmp_root.mkdir(parents=True, exist_ok=True)
        self.task_dir = self.tmp_root / "100"
        self.task_dir.mkdir(parents=True, exist_ok=True)
        self.valid_pdf = self.task_dir / "source.pdf"
        self.valid_pdf.write_bytes(b"%PDF-1.4\n%fake pdf content\n")

    def tearDown(self):
        import shutil
        if self.tmp_root.exists():
            shutil.rmtree(self.tmp_root, ignore_errors=True)

    def test_etc_passwd_rejected(self):
        from app.services.path_guard import resolve_safe_path
        with self.assertRaises(HTTPException) as ctx:
            resolve_safe_path("/etc/passwd", "pdf")
        self.assertIn(ctx.exception.status_code, (400, 403))

    def test_parent_traversal_rejected(self):
        from app.services.path_guard import resolve_safe_path
        # 模拟 ../etc/passwd（构造一个 resolved 后逃出 ALLOWED_ROOT 的路径）
        bad_path = str(self.tmp_root / ".." / "etc" / "passwd")
        with self.assertRaises(HTTPException) as ctx:
            resolve_safe_path(bad_path, "pdf")
        self.assertEqual(ctx.exception.status_code, 403)
        self.assertIn("ALLOWED_DOCUMENT_ROOT", str(ctx.exception.detail))

    def test_exe_extension_rejected(self):
        from app.services.path_guard import resolve_safe_path
        bad_file = self.task_dir / "source.exe"
        bad_file.write_bytes(b"MZ")  # 假 EXE 文件头
        with self.assertRaises(HTTPException) as ctx:
            resolve_safe_path(str(bad_file), "exe")
        self.assertEqual(ctx.exception.status_code, 400)
        self.assertIn("extension not allowed", str(ctx.exception.detail))

    def test_extension_mismatch_rejected(self):
        from app.services.path_guard import resolve_safe_path
        with self.assertRaises(HTTPException) as ctx:
            resolve_safe_path(str(self.valid_pdf), "docx")
        self.assertEqual(ctx.exception.status_code, 400)
        self.assertIn("extension mismatch", str(ctx.exception.detail))

    def test_valid_pdf_accepted(self):
        from app.services.path_guard import resolve_safe_path
        result = resolve_safe_path(str(self.valid_pdf), "pdf")
        self.assertEqual(result, self.valid_pdf.resolve())

    def test_file_too_large_rejected(self):
        from app.services.path_guard import resolve_safe_path, MAX_FILE_SIZE
        big_file = self.task_dir / "source.pdf"
        big_file.write_bytes(b"x" * (MAX_FILE_SIZE + 1))
        with self.assertRaises(HTTPException) as ctx:
            resolve_safe_path(str(big_file), "pdf")
        self.assertEqual(ctx.exception.status_code, 413)

    def test_file_not_found_rejected(self):
        from app.services.path_guard import resolve_safe_path
        missing = self.task_dir / "missing.pdf"
        with self.assertRaises(HTTPException) as ctx:
            resolve_safe_path(str(missing), "pdf")
        self.assertEqual(ctx.exception.status_code, 404)


if __name__ == "__main__":
    unittest.main()