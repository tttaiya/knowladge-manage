from __future__ import annotations

import math
import re
from dataclasses import dataclass
from typing import Optional

from app.schemas import ChunkPayload, ParsedBlock, TaskPayload
from app.services.parsers.text_utils import clean_text, is_heading_line, update_chapter_path


TOKEN_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]|[A-Za-z0-9_]+|[^\s]")
LIST_ITEM_RE = re.compile(
    r"^(?:[-*•●▪]\s+|\d+[.)、]\s*|[一二三四五六七八九十]+、|"
    r"[（(][一二三四五六七八九十0-9]+[）)]\s*)"
)
SENTENCE_END_RE = re.compile(r"[。！？!?；;.]$")


@dataclass
class Unit:
    content: str
    page_no: Optional[int]
    chunk_type: str
    chapter_path: Optional[str]


def chunk_blocks(blocks: list[ParsedBlock], payload: TaskPayload) -> list[ChunkPayload]:
    """统一切片入口：结构感知、多策略、自适应长度与双重上限保护。"""
    if not blocks:
        return []

    chunk_size = payload.chunk_size
    overlap = payload.overlap
    if overlap >= chunk_size:
        overlap = max(0, chunk_size // 10)

    if payload.chunk_mode == "heading":
        chunks = heading_chunk(blocks, chunk_size, overlap, payload.separators())
    else:
        chunks = fixed_chunk(blocks, chunk_size, overlap, payload.separators())

    for index, chunk in enumerate(chunks, start=1):
        chunk.chunk_index = index
        chunk.char_count = len(chunk.content or "")
    return chunks


def fixed_chunk(
    blocks: list[ParsedBlock],
    chunk_size: int,
    overlap: int,
    separators: list[str],
) -> list[ChunkPayload]:
    """固定模式：先合并同源短段，再按内容类型选择切片策略。"""
    units = merge_adjacent_units(
        [
            Unit(
                content=clean_text(block.content),
                page_no=block.page_no,
                chunk_type=block.block_type or "paragraph",
                chapter_path=block.chapter_path,
            )
            for block in blocks
            if clean_text(block.content)
        ]
    )
    return chunks_from_units(units, chunk_size, overlap, separators)


def heading_chunk(
    blocks: list[ParsedBlock],
    chunk_size: int,
    overlap: int,
    separators: list[str],
) -> list[ChunkPayload]:
    """标题模式：继承或识别章节路径，再执行内容类型感知切片。"""
    units: list[Unit] = []
    heading_levels: list[str] = []

    for block in blocks:
        text = clean_text(block.content)
        if not text:
            continue

        inherited_path = block.chapter_path
        lines = [line.strip() for line in text.split("\n") if line.strip()]
        buffer: list[str] = []
        current_path = inherited_path or ("/".join(heading_levels) if heading_levels else None)

        def flush() -> None:
            nonlocal buffer, current_path
            content = clean_text("\n".join(buffer))
            if content:
                units.append(
                    Unit(
                        content=content,
                        page_no=block.page_no,
                        chunk_type=block.block_type or "paragraph",
                        chapter_path=current_path,
                    )
                )
            buffer = []

        for line in lines:
            is_heading, _ = is_heading_line(line)
            if is_heading:
                flush()
                current_path = update_chapter_path(line, heading_levels)
                buffer.append(line)
            else:
                if not inherited_path:
                    current_path = "/".join(heading_levels) if heading_levels else current_path
                buffer.append(line)
        flush()

    return chunks_from_units(
        merge_adjacent_units(units), chunk_size, overlap, separators
    )


def merge_adjacent_units(units: list[Unit]) -> list[Unit]:
    """合并同页、同章节、同类型的相邻段落，避免产生大量微小 chunk。"""
    merged: list[Unit] = []
    for unit in units:
        content = clean_text(unit.content)
        if not content:
            continue
        if merged and same_source(merged[-1], unit):
            merged[-1].content = clean_text(merged[-1].content + "\n\n" + content)
        else:
            merged.append(
                Unit(
                    content=content,
                    page_no=unit.page_no,
                    chunk_type=unit.chunk_type,
                    chapter_path=unit.chapter_path,
                )
            )
    return merged


def same_source(left: Unit, right: Unit) -> bool:
    return (
        left.page_no == right.page_no
        and left.chapter_path == right.chapter_path
        and left.chunk_type == right.chunk_type
    )


def chunks_from_units(
    units: list[Unit],
    chunk_size: int,
    overlap: int,
    separators: list[str],
) -> list[ChunkPayload]:
    chunks: list[ChunkPayload] = []
    for unit in units:
        effective_size = adaptive_chunk_size(chunk_size, unit.chunk_type, unit.content)
        pieces = split_unit(unit, effective_size, overlap, separators)
        for piece in pieces:
            chunks.append(
                ChunkPayload(
                    content=piece,
                    chapterPath=unit.chapter_path,
                    pageNo=unit.page_no,
                    chunkType=unit.chunk_type,
                    charCount=len(piece),
                    chunkIndex=len(chunks) + 1,
                )
            )
    return chunks


def split_unit(
    unit: Unit,
    chunk_size: int,
    overlap: int,
    separators: list[str],
) -> list[str]:
    chunk_type = (unit.chunk_type or "paragraph").lower()
    if chunk_type == "table":
        return split_table_text(unit.content, chunk_size, separators)
    if looks_like_list(unit.content):
        return split_list_text(unit.content, chunk_size, separators)
    return split_text(unit.content, chunk_size, overlap, separators)


def adaptive_chunk_size(max_size: int, chunk_type: str, text: str) -> int:
    """按内容类型和噪声比例收紧目标长度，但永远不超过调用方上限。"""
    factor = 1.0
    normalized_type = (chunk_type or "paragraph").lower()
    if normalized_type == "ocr":
        factor = 0.80
    elif normalized_type == "table":
        factor = 0.95
    elif looks_like_list(text):
        factor = 0.90
    if noise_ratio(text) > 0.15:
        factor = min(factor, 0.75)
    return min(max_size, max(32, int(max_size * factor)))


def split_text(
    text: str,
    chunk_size: int,
    overlap: int,
    separators: list[str],
) -> list[str]:
    """递归切分，使用字符数和轻量 Token 估算双重限制，并添加安全重叠。"""
    text = clean_text(text)
    if not text:
        return []

    token_limit = token_budget_for_size(chunk_size)
    if fits_limits(text, chunk_size, token_limit):
        return [text]

    overlap = max(0, min(overlap, chunk_size - 1))
    content_limit = chunk_size - overlap if overlap else chunk_size
    content_token_limit = max(8, token_limit - min(overlap, token_limit // 3))
    base_chunks = recursive_split(
        text,
        content_limit,
        normalize_separators(separators),
        content_token_limit,
    )
    base_chunks = [clean_text(chunk) for chunk in base_chunks if clean_text(chunk)]
    base_chunks = rebalance_low_quality_chunks(
        base_chunks, content_limit, content_token_limit
    )

    if overlap <= 0 or len(base_chunks) <= 1:
        return base_chunks
    return add_safe_overlap(base_chunks, overlap, chunk_size, token_limit)


def recursive_split(
    text: str,
    limit: int,
    separators: list[str],
    token_limit: Optional[int] = None,
) -> list[str]:
    """段落→换行→句号→标点→空格逐级降级，最后按双重上限硬切。"""
    token_limit = token_limit or token_budget_for_size(limit)
    if fits_limits(text, limit, token_limit):
        return [text]
    if not separators:
        return hard_split_by_limits(text, limit, token_limit)

    separator = separators[0]
    if not separator or separator not in text:
        return recursive_split(text, limit, separators[1:], token_limit)

    pieces = split_keep_separator(text, separator)
    if len(pieces) <= 1:
        return recursive_split(text, limit, separators[1:], token_limit)

    result: list[str] = []
    current = ""

    def flush() -> None:
        nonlocal current
        value = clean_text(current)
        if value:
            result.append(value)
        current = ""

    for piece in pieces:
        if not piece.strip():
            continue
        if not fits_limits(piece, limit, token_limit):
            flush()
            result.extend(
                recursive_split(piece, limit, separators[1:], token_limit)
            )
            continue
        candidate = current + piece
        if not current or fits_limits(candidate, limit, token_limit):
            current = candidate
        else:
            flush()
            current = piece
    flush()
    return result


def split_table_text(text: str, chunk_size: int, separators: list[str]) -> list[str]:
    """表格按行切分；每个后续 chunk 重复表头，避免字段含义丢失。"""
    text = clean_text(text)
    lines = [line for line in text.split("\n") if line.strip()]
    if len(lines) < 2:
        return split_text(text, chunk_size, 0, separators)

    header_count = 2 if lines[0].startswith("工作表：") and len(lines) > 2 else 1
    header = clean_text("\n".join(lines[:header_count]))
    rows = lines[header_count:]
    token_limit = token_budget_for_size(chunk_size)
    if not fits_limits(header, chunk_size, token_limit):
        return split_text(text, chunk_size, 0, separators)

    chunks: list[str] = []
    current = header
    has_data = False
    for row in rows:
        candidate = clean_text(current + "\n" + row)
        if fits_limits(candidate, chunk_size, token_limit):
            current = candidate
            has_data = True
            continue

        if has_data:
            chunks.append(current)
        row_char_limit = max(8, chunk_size - len(header) - 1)
        row_token_limit = max(4, token_limit - estimate_token_count(header) - 1)
        row_parts = recursive_split(
            row,
            row_char_limit,
            normalize_separators(separators),
            row_token_limit,
        )
        for row_part in row_parts[:-1]:
            chunks.append(clean_text(header + "\n" + row_part))
        current = clean_text(header + "\n" + row_parts[-1])
        has_data = True

    if has_data:
        chunks.append(current)
    elif header:
        chunks.append(header)
    return chunks


def split_list_text(text: str, chunk_size: int, separators: list[str]) -> list[str]:
    """列表以完整条目为最小单元；超长条目才退回递归切分。"""
    preamble, items = extract_list_items(text)
    if len(items) < 2:
        return split_text(text, chunk_size, 0, separators)

    token_limit = token_budget_for_size(chunk_size)
    chunks: list[str] = []
    current = preamble
    if current and not fits_limits(current, chunk_size, token_limit):
        chunks.extend(
            recursive_split(
                current,
                chunk_size,
                normalize_separators(separators),
                token_limit,
            )
        )
        current = ""
    for item in items:
        candidate = clean_text((current + "\n" if current else "") + item)
        if fits_limits(candidate, chunk_size, token_limit):
            current = candidate
            continue
        if current:
            chunks.append(current)
        item_parts = recursive_split(
            item,
            chunk_size,
            normalize_separators(separators),
            token_limit,
        )
        chunks.extend(item_parts[:-1])
        current = item_parts[-1]
    if current:
        chunks.append(current)
    return rebalance_low_quality_chunks(chunks, chunk_size, token_limit)


def extract_list_items(text: str) -> tuple[str, list[str]]:
    preamble_lines: list[str] = []
    items: list[str] = []
    current: list[str] = []
    for line in [line.strip() for line in clean_text(text).split("\n") if line.strip()]:
        if LIST_ITEM_RE.match(line):
            if current:
                items.append(clean_text("\n".join(current)))
            current = [line]
        elif current:
            current.append(line)
        else:
            preamble_lines.append(line)
    if current:
        items.append(clean_text("\n".join(current)))
    return clean_text("\n".join(preamble_lines)), items


def looks_like_list(text: str) -> bool:
    lines = [line.strip() for line in clean_text(text).split("\n") if line.strip()]
    return sum(1 for line in lines if LIST_ITEM_RE.match(line)) >= 2


def estimate_token_count(text: str) -> int:
    """无模型依赖的保守估算：中文单字、标点和英文约四字符分别计 Token。"""
    count = 0
    for token in TOKEN_RE.findall(text or ""):
        if len(token) == 1 and "\u3400" <= token <= "\u9fff":
            count += 1
        elif token.isascii() and (token.isalnum() or "_" in token):
            count += max(1, math.ceil(len(token) / 4))
        else:
            count += 1
    return count


def token_budget_for_size(chunk_size: int) -> int:
    return max(24, int(chunk_size * 0.85))


def fits_limits(text: str, char_limit: int, token_limit: int) -> bool:
    return len(text) <= char_limit and estimate_token_count(text) <= token_limit


def add_safe_overlap(
    base_chunks: list[str],
    overlap: int,
    char_limit: int,
    token_limit: int,
) -> list[str]:
    result = [base_chunks[0]]
    for previous, current in zip(base_chunks, base_chunks[1:]):
        prefix = clean_text(previous[-overlap:]) if overlap else ""
        combined = clean_text((prefix + "\n" if prefix else "") + current)
        while prefix and not fits_limits(combined, char_limit, token_limit):
            prefix = prefix[1:]
            combined = clean_text((prefix + "\n" if prefix else "") + current)
        result.append(combined if fits_limits(combined, char_limit, token_limit) else current)
    return result


def chunk_quality_score(text: str, target_size: int) -> int:
    """内部质量分（0-100）：评估长度、可读字符比例和边界完整性。"""
    text = clean_text(text)
    if not text:
        return 0
    compact = re.sub(r"\s+", "", text)
    useful = sum(1 for char in compact if char.isalnum() or "\u3400" <= char <= "\u9fff")
    readable_ratio = useful / max(1, len(compact))
    preferred_min = max(20, int(target_size * 0.20))
    length_score = min(40, int(40 * len(text) / preferred_min))
    readable_score = min(40, int(readable_ratio * 50))
    boundary_score = 15 if SENTENCE_END_RE.search(text) else 8
    structure_score = 5 if "\n" in text or LIST_ITEM_RE.match(text) else 0
    return min(100, length_score + readable_score + boundary_score + structure_score)


def rebalance_low_quality_chunks(
    chunks: list[str],
    char_limit: int,
    token_limit: int,
) -> list[str]:
    """不丢内容；只在不越界时把过短/低质量碎片并回相邻 chunk。"""
    result: list[str] = []
    for chunk in [clean_text(value) for value in chunks if clean_text(value)]:
        low_quality = (
            len(chunk) < max(12, int(char_limit * 0.12))
            or chunk_quality_score(chunk, char_limit) < 45
        )
        if result and low_quality:
            candidate = clean_text(result[-1] + "\n" + chunk)
            if fits_limits(candidate, char_limit, token_limit):
                result[-1] = candidate
                continue
        result.append(chunk)
    if len(result) > 1:
        first = result[0]
        if len(first) < max(12, int(char_limit * 0.12)):
            candidate = clean_text(first + "\n" + result[1])
            if fits_limits(candidate, char_limit, token_limit):
                result[:2] = [candidate]
    return result


def noise_ratio(text: str) -> float:
    compact = re.sub(r"\s+", "", text or "")
    if not compact:
        return 0.0
    noisy = sum(
        1
        for char in compact
        if not (char.isalnum() or "\u3400" <= char <= "\u9fff")
        and char not in "，。！？；：、,.!?;:()（）[]【】《》\"'_-"
    )
    return noisy / len(compact)


def hard_split_by_limits(text: str, char_limit: int, token_limit: int) -> list[str]:
    result: list[str] = []
    current = ""
    for char in text:
        candidate = current + char
        if current and not fits_limits(candidate, char_limit, token_limit):
            value = clean_text(current)
            if value:
                result.append(value)
            current = char
        else:
            current = candidate
    value = clean_text(current)
    if value:
        result.append(value)
    return result


def split_keep_separator(text: str, separator: str) -> list[str]:
    tokens = re.split("(" + re.escape(separator) + ")", text)
    pieces: list[str] = []
    index = 0
    while index < len(tokens):
        value = tokens[index]
        if index + 1 < len(tokens):
            value += tokens[index + 1]
        if value:
            pieces.append(value)
        index += 2
    return pieces


def normalize_separators(separators: list[str]) -> list[str]:
    defaults = ["\n\n", "\n", "。", "！", "？", "；", ";", "，", ",", " "]
    result: list[str] = []
    for separator in list(separators or []) + defaults:
        if separator and separator not in result:
            result.append(separator)
    return result


def split_to_units(text: str, separators: list[str]) -> list[str]:
    """兼容旧调用：返回第一个可用分隔层级的片段。"""
    for separator in normalize_separators(separators):
        if separator in text:
            pieces = [clean_text(value) for value in split_keep_separator(text, separator)]
            pieces = [value for value in pieces if value]
            if len(pieces) > 1:
                return pieces
    return [clean_text(text)] if clean_text(text) else []


def hard_split(text: str, chunk_size: int, overlap: int) -> list[str]:
    """兼容旧调用，同时应用 Token 保护和安全重叠。"""
    text = clean_text(text)
    if not text:
        return []
    token_limit = token_budget_for_size(chunk_size)
    base = hard_split_by_limits(
        text,
        max(1, chunk_size - max(0, overlap)),
        max(8, token_limit - min(max(0, overlap), token_limit // 3)),
    )
    if overlap <= 0 or len(base) <= 1:
        return base
    return add_safe_overlap(base, overlap, chunk_size, token_limit)
