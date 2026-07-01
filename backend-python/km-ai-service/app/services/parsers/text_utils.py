from __future__ import annotations

from collections import Counter
import re
import unicodedata
from pathlib import Path
from typing import Optional


CONTROL_CHARACTERS_RE = re.compile(
    r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f-\x9f]"
)
ZERO_WIDTH_CHARACTERS_RE = re.compile(r"[\u200b-\u200d\u2060\ufeff]")
SPACED_CJK_RE = re.compile(
    r"(?:[\u3400-\u4dbf\u4e00-\u9fff][ ]+){3,}"
    r"[\u3400-\u4dbf\u4e00-\u9fff]"
)
PAGE_NUMBER_PATTERNS = (
    re.compile(r"^\s*[-—–]?\s*\d+\s*[-—–]?\s*$", re.IGNORECASE),
    re.compile(r"^\s*第\s*\d+\s*页(?:\s*共\s*\d+\s*页)?\s*$", re.IGNORECASE),
    re.compile(r"^\s*(?:page|p\.)\s*\d+(?:\s*(?:of|/)\s*\d+)?\s*$", re.IGNORECASE),
    re.compile(r"^\s*\d+\s*/\s*\d+\s*$", re.IGNORECASE),
)
STRUCTURAL_LINE_RE = re.compile(
    r"^(?:[-*•●▪]\s+|\d+[.)、]\s*|[一二三四五六七八九十]+、|"
    r"第[一二三四五六七八九十百千万0-9]+[章节篇部分])"
)
SENTENCE_ENDINGS = tuple("。！？!?；;：:")


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
    """安全的字符级清洗：移除不可见噪声并保留段落结构。"""
    if not text:
        return ""
    text = unicodedata.normalize("NFC", str(text))
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = text.replace("\u3000", " ").replace("\u00a0", " ").replace("\u202f", " ")
    text = ZERO_WIDTH_CHARACTERS_RE.sub("", text)
    text = CONTROL_CHARACTERS_RE.sub("", text)
    # U+FFFD 表示上游解码失败。用空格隔开，避免错误地把前后单词粘在一起。
    text = text.replace("\ufffd", " ")
    text = SPACED_CJK_RE.sub(lambda match: re.sub(r" +", "", match.group(0)), text)
    lines = [line.strip() for line in text.split("\n")]
    text = "\n".join(lines)
    text = re.sub(r"[ \t]{2,}", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def clean_document_text(text: str) -> str:
    """面向正文/OCR 的清洗：在字符清洗后去掉连续的重复行。"""
    text = clean_text(text)
    if not text:
        return ""

    result: list[str] = []
    previous_content: Optional[str] = None
    for line in text.split("\n"):
        normalized = re.sub(r"\s+", " ", line).strip().casefold()
        if normalized and normalized == previous_content and len(normalized) >= 6:
            continue
        result.append(line)
        if normalized:
            previous_content = normalized
        elif result and result[-1] == "":
            # 空行保留段落边界，但不影响相邻重复行判断。
            continue
    return clean_text("\n".join(result))


def clean_pdf_pages(page_texts: list[str]) -> list[str]:
    """清洗 PDF/OCR 页面，并保守移除重复页眉、页脚和页码。"""
    cleaned = [clean_document_text(text) for text in page_texts]
    cleaned = remove_repeated_page_margins(cleaned)
    return [repair_soft_line_breaks(text) for text in cleaned]


def remove_repeated_page_margins(page_texts: list[str]) -> list[str]:
    """仅在至少三页且多数页面重复时，删除顶部/底部两行中的页眉页脚。"""
    if not page_texts:
        return []

    page_lines = [text.split("\n") if text else [] for text in page_texts]
    page_positions = [_non_empty_positions(lines) for lines in page_lines]
    page_count = len(page_lines)

    top_counts: Counter[str] = Counter()
    bottom_counts: Counter[str] = Counter()
    for lines, positions in zip(page_lines, page_positions):
        top_keys = {
            _margin_key(lines[index])
            for index in positions[:2]
            if not is_page_number_line(lines[index])
        }
        bottom_keys = {
            _margin_key(lines[index])
            for index in positions[-2:]
            if not is_page_number_line(lines[index])
        }
        top_counts.update(key for key in top_keys if key)
        bottom_counts.update(key for key in bottom_keys if key)

    # 2/3 多数且至少出现三次，降低误删正文标题的风险。
    threshold = max(3, (page_count * 2 + 2) // 3)
    repeated_top = {key for key, count in top_counts.items() if count >= threshold}
    repeated_bottom = {key for key, count in bottom_counts.items() if count >= threshold}

    result: list[str] = []
    for lines, positions in zip(page_lines, page_positions):
        remove_indexes: set[int] = set()
        margin_indexes = set(positions[:2] + positions[-2:])
        for index in margin_indexes:
            line = lines[index]
            if is_page_number_line(line):
                remove_indexes.add(index)
        for index in positions[:2]:
            if _margin_key(lines[index]) in repeated_top:
                remove_indexes.add(index)
        for index in positions[-2:]:
            if _margin_key(lines[index]) in repeated_bottom:
                remove_indexes.add(index)

        kept = [line for index, line in enumerate(lines) if index not in remove_indexes]
        result.append(clean_text("\n".join(kept)))
    return result


def repair_soft_line_breaks(text: str) -> str:
    """修复 PDF/OCR 排版造成的句中换行，同时保留标题、列表和段落边界。"""
    text = clean_text(text)
    if not text:
        return ""

    repaired_paragraphs: list[str] = []
    for paragraph in re.split(r"\n\s*\n", text):
        lines = [line.strip() for line in paragraph.split("\n") if line.strip()]
        if not lines:
            continue
        merged: list[str] = [lines[0]]
        for current in lines[1:]:
            previous = merged[-1]
            if _can_join_soft_wrapped_lines(previous, current):
                if (
                    previous.endswith("-")
                    and len(previous) > 1
                    and previous[-2].isascii()
                    and current[:1].isascii()
                    and current[:1].islower()
                ):
                    merged[-1] = previous[:-1] + current
                else:
                    joiner = " " if _needs_word_space(previous, current) else ""
                    merged[-1] = previous + joiner + current
            else:
                merged.append(current)
        repaired_paragraphs.append("\n".join(merged))
    return clean_text("\n\n".join(repaired_paragraphs))


def is_page_number_line(line: str) -> bool:
    value = clean_text(line)
    return bool(value) and any(pattern.fullmatch(value) for pattern in PAGE_NUMBER_PATTERNS)


def _non_empty_positions(lines: list[str]) -> list[int]:
    return [index for index, line in enumerate(lines) if line.strip()]


def _margin_key(line: str) -> str:
    value = clean_text(line).casefold()
    value = re.sub(r"\d+", "#", value)
    return re.sub(r"\s+", "", value)


def _can_join_soft_wrapped_lines(previous: str, current: str) -> bool:
    if not previous or not current:
        return False
    if previous.endswith(SENTENCE_ENDINGS):
        return False
    if STRUCTURAL_LINE_RE.match(previous) or STRUCTURAL_LINE_RE.match(current):
        return False
    if is_heading_line(previous)[0] or is_heading_line(current)[0]:
        return False
    return True


def _needs_word_space(previous: str, current: str) -> bool:
    return bool(previous and current and previous[-1].isascii() and current[0].isascii())


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
