import sys
from types import SimpleNamespace
from unittest.mock import patch

from app.services.ocr.paddle_ocr import get_ocr_engine


def test_ocr_cpu_safe_options_are_applied():
    captured = {}

    def fake_paddle_ocr(**kwargs):
        captured.update(kwargs)
        return object()

    get_ocr_engine.cache_clear()
    with (
        patch.dict(sys.modules, {"paddleocr": SimpleNamespace(PaddleOCR=fake_paddle_ocr)}),
        patch.dict("os.environ", {"OCR_CPU_THREADS": "1"}),
    ):
        get_ocr_engine()
    get_ocr_engine.cache_clear()

    assert captured["use_gpu"] is False
    assert captured["enable_mkldnn"] is False
    assert captured["ir_optim"] is False
    assert captured["cpu_threads"] == 1
