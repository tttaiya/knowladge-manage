from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from typing import Optional


@lru_cache(maxsize=1)
def get_ocr_engine():
    """懒加载 PaddleOCR。普通 PDF/DOCX/TXT 不会加载，只有图片/扫描 PDF 才加载。"""
    try:
        from paddleocr import PaddleOCR
    except Exception as exc:
        raise RuntimeError(
            "当前环境没有安装 PaddleOCR，无法进行 OCR。"
            "请执行：pip install paddleocr paddlepaddle"
        ) from exc

    try:
        return PaddleOCR(use_angle_cls=True, lang="ch", show_log=False)
    except (TypeError, ValueError) as exc:
        # PaddleOCR 3.x 删除了 show_log，并将未知参数异常改成了 ValueError。
        if "show_log" not in str(exc):
            raise
        return PaddleOCR(use_angle_cls=True, lang="ch")


def ocr_image(image_path: str | Path) -> tuple[str, Optional[float]]:
    """识别单张图片，返回识别文本和平均置信度。"""
    image_path = str(image_path)
    ocr = get_ocr_engine()

    try:
        result = ocr.ocr(image_path, cls=True)
    except (TypeError, ValueError) as exc:
        # PaddleOCR 3.x 不再接受旧的 cls 参数。
        if "cls" not in str(exc):
            raise
        result = ocr.ocr(image_path)

    texts: list[str] = []
    scores: list[float] = []

    def visit(node):
        # PaddleOCR 常见结构：[[[box, (text, score)], ...]]
        if isinstance(node, tuple) and len(node) >= 2 and isinstance(node[0], str):
            texts.append(node[0])
            try:
                scores.append(float(node[1]))
            except Exception:
                pass
        elif isinstance(node, list):
            for item in node:
                visit(item)

    visit(result)
    text = "\n".join(t for t in texts if t and t.strip())
    avg_score = sum(scores) / len(scores) if scores else None
    return text, avg_score
