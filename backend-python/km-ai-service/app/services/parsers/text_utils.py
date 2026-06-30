from __future__ import annotations

import re
from pathlib import Path
from typing import Optional


def ensure_file_exists(file_path: str) -> Path:
    path = Path(file_path)
    if not path.exists() or not path.is_file():
        raise FileNotFoundError(f"文件不存在或不是普通文件：{file_path}")
    return path


def guess_extension(file_path: str, extension: Optional[str] = None) -> str:
    if extension:
        return extension.lower().strip().lstrip(".")
    return Path(file_path).suffix.lower().strip().lstrip(".")


def clean_text(text: str) -> str:
    """基础清洗：去掉多余空白，保留段落。"""
    if not text:
        return ""
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = text.replace("\u3000", " ")
    lines = [line.strip() for line in text.split("\n")]
    text = "\n".join(lines)
    text = re.sub(r"[ \t]{2,}", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def read_text_file(path: Path) -> str:
    for enc in ("utf-8", "utf-8-sig", "gbk", "gb18030"):
        try:
            return path.read_text(encoding=enc)
        except UnicodeDecodeError:
            continue
    return path.read_text(encoding="utf-8", errors="ignore")


def is_heading_line(line: str) -> tuple[bool, int]:
    """识别常见标题：第一章、一、、1.、1.1、1.1.1、（一）。返回是否标题和层级。"""
    line = line.strip()
    if not line or len(line) > 100:
        return False, 0
    patterns = [
        (1, r"^第[一二三四五六七八九十百千万0-9]+[章节篇部分].{0,80}$"),
        (2, r"^[一二三四五六七八九十]+、.{1,80}$"),
        (3, r"^\d+[、.．]\s*.{1,80}$"),
        (4, r"^\d+\.\d+(?:\.\d+)*\s*.{0,80}$"),
        (5, r"^[（(][一二三四五六七八九十0-9]+[）)].{1,80}$"),
    ]
    for level, pattern in patterns:
        if re.match(pattern, line):
            return True, level
    return False, 0


def update_chapter_path(line: str, current_levels: list[str]) -> Optional[str]:
    is_heading, level = is_heading_line(line)
    if not is_heading:
        return "/".join(current_levels) if current_levels else None
    idx = max(0, level - 1)
    while len(current_levels) <= idx:
        current_levels.append("")
    current_levels[idx] = line.strip()
    del current_levels[idx + 1 :]
    return "/".join([item for item in current_levels if item]) or line.strip()
