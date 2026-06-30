"""
F4 集成测试根 conftest：保证 INTERNAL_TOKEN 环境变量在所有测试模块加载前设置。

避免 test_internal_token 测试清空 INTERNAL_TOKEN 后污染其它测试模块的 app.middleware 模块常量。
"""
import os

# 必须在 import app.* 之前设值
os.environ.setdefault("INTERNAL_TOKEN", "test-internal-token")
os.environ.setdefault("ALLOWED_DOCUMENT_ROOT", "/tmp/f4-test-task-files")