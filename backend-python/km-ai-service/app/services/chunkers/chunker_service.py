from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Optional

from app.schemas import ChunkPayload, ParsedBlock, TaskPayload
from app.services.parsers.text_utils import clean_text, is_heading_line, update_chapter_path


@dataclass
class Unit:
    content: str
    page_no: Optional[int]
    chunk_type: str
    chapter_path: Optional[str]


def chunk_blocks(blocks: list[ParsedBlock], payload: TaskPayload) -> list[ChunkPayload]:
    """统一切片入口。"""
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

    for i, chunk in enumerate(chunks, start=1):
        chunk.chunk_index = i
        chunk.char_count = len(chunk.content or "")
    return chunks


def fixed_chunk(blocks: list[ParsedBlock], chunk_size: int, overlap: int, separators: list[str]) -> list[ChunkPayload]:
    """固定长度递归切片基础版：按段落/句号尽量切，不够再硬切。"""
    chunks: list[ChunkPayload] = []

    for block in blocks:
        text = clean_text(block.content)
        if not text:
            continue
        pieces = split_text(text, chunk_size, overlap, separators)
        for piece in pieces:
            chunks.append(
                ChunkPayload(
                    content=piece,
                    chapterPath=block.chapter_path,
                    pageNo=block.page_no,
                    chunkType=block.block_type or "paragraph",
                    charCount=len(piece),
                    chunkIndex=len(chunks) + 1,
                )
            )
    return chunks


def heading_chunk(blocks: list[ParsedBlock], chunk_size: int, overlap: int, separators: list[str]) -> list[ChunkPayload]:
    """标题层级切片基础版：识别 第一章 / 一、 / 1. / 1.1 / 1.1.1。"""
    units: list[Unit] = []
    heading_levels: list[str] = []

    for block in blocks:
        text = clean_text(block.content)
        if not text:
            continue

        # 如果解析阶段已经有 chapterPath，优先继承；否则按行识别标题。
        inherited_path = block.chapter_path
        lines = [line.strip() for line in text.split("\n") if line.strip()]
        buffer: list[str] = []
        current_path = inherited_path or ("/".join(heading_levels) if heading_levels else None)

        def flush():
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
                # 标题本身也放进内容，避免检索时丢上下文。
                buffer.append(line)
            else:
                if not inherited_path:
                    current_path = "/".join(heading_levels) if heading_levels else current_path
                buffer.append(line)
        flush()

    # 每个标题 section 内再按 chunk_size 切。
    chunks: list[ChunkPayload] = []
    for unit in units:
        for piece in split_text(unit.content, chunk_size, overlap, separators):
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


def split_text(text: str, chunk_size: int, overlap: int, separators: list[str]) -> list[str]:
    """尽量按分隔符组合成 chunk；超长内容再按窗口硬切。"""
    text = clean_text(text)
    if not text:
        return []
    if len(text) <= chunk_size:
        return [text]

    units = split_to_units(text, separators)
    chunks: list[str] = []
    current = ""

    def push_current():
        nonlocal current
        current = clean_text(current)
        if current:
            chunks.append(current)
        current = ""

    for unit in units:
        unit = clean_text(unit)
        if not unit:
            continue

        if len(unit) > chunk_size:
            push_current()
            chunks.extend(hard_split(unit, chunk_size, overlap))
            continue

        joiner = "\n" if current else ""
        if len(current) + len(joiner) + len(unit) <= chunk_size:
            current = current + joiner + unit
        else:
            push_current()
            current = unit

    push_current()

    # 给相邻 chunk 增加一点重叠上下文。
    if overlap > 0 and len(chunks) > 1:
        with_overlap = [chunks[0]]
        for prev, cur in zip(chunks, chunks[1:]):
            prefix = prev[-overlap:]
            merged = clean_text(prefix + "\n" + cur)
            with_overlap.append(merged)
        chunks = with_overlap

    return [chunk for chunk in chunks if chunk]


def split_to_units(text: str, separators: list[str]) -> list[str]:
    """优先用第一个能把文本切开的分隔符。"""
    for sep in separators:
        if sep and sep in text:
            parts = [part.strip() for part in text.split(sep) if part.strip()]
            if len(parts) > 1:
                # 中文句号等分隔符切开后补回去，避免语义太碎。
                if sep in {"。", "；", ";", "，", ","}:
                    return [part + sep for part in parts]
                return parts
    return [text]


def hard_split(text: str, chunk_size: int, overlap: int) -> list[str]:
    result = []
    start = 0
    length = len(text)
    step = max(1, chunk_size - overlap)
    while start < length:
        piece = clean_text(text[start : start + chunk_size])
        if piece:
            result.append(piece)
        start += step
    return result
