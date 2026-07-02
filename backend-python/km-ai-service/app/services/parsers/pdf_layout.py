from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Iterable


@dataclass(frozen=True)
class TextBlock:
    x0: float
    y0: float
    x1: float
    y1: float
    text: str

    @property
    def width(self) -> float:
        return max(0.0, self.x1 - self.x0)


def extract_page_text(page: Any) -> str:
    """使用坐标块重建阅读顺序；不支持 blocks 时退回普通文本提取。"""
    try:
        raw_blocks = page.get_text("blocks") or []
    except Exception:
        return page.get_text("text") or ""

    if not raw_blocks:
        try:
            return page.get_text("text") or ""
        except Exception:
            return ""

    page_width = _page_width(page, raw_blocks)
    ordered = order_pdf_text_blocks(raw_blocks, page_width)
    if ordered.strip():
        return ordered
    try:
        return page.get_text("text") or ""
    except Exception:
        return ""


def order_pdf_text_blocks(raw_blocks: Iterable[Any], page_width: float) -> str:
    """横跨页面的标题按纵向插入；各区间内按栏从左到右、栏内从上到下。"""
    blocks = [_to_text_block(item) for item in raw_blocks]
    blocks = [block for block in blocks if block is not None and block.text.strip()]
    if not blocks:
        return ""

    page_width = page_width or max(block.x1 for block in blocks)
    spanning: list[TextBlock] = []
    narrow: list[TextBlock] = []
    for block in blocks:
        crosses_center = block.x0 <= page_width * 0.25 and block.x1 >= page_width * 0.75
        if block.width >= page_width * 0.62 or crosses_center:
            spanning.append(block)
        else:
            narrow.append(block)

    spanning.sort(key=lambda block: (block.y0, block.x0))
    remaining = list(narrow)
    ordered: list[TextBlock] = []
    for wide_block in spanning:
        before = [block for block in remaining if block.y0 < wide_block.y0]
        ordered.extend(_order_region(before, page_width))
        before_ids = {id(block) for block in before}
        remaining = [block for block in remaining if id(block) not in before_ids]
        ordered.append(wide_block)
    ordered.extend(_order_region(remaining, page_width))

    return "\n\n".join(block.text.strip() for block in ordered if block.text.strip())


def _order_region(blocks: list[TextBlock], page_width: float) -> list[TextBlock]:
    if len(blocks) <= 1:
        return sorted(blocks, key=lambda block: (block.y0, block.x0))

    tolerance = max(24.0, page_width * 0.08)
    columns: list[list[TextBlock]] = []
    for block in sorted(blocks, key=lambda value: (value.x0, value.y0)):
        best_column = None
        best_distance = None
        for column in columns:
            anchor = sum(item.x0 for item in column) / len(column)
            distance = abs(block.x0 - anchor)
            if distance <= tolerance and (best_distance is None or distance < best_distance):
                best_column = column
                best_distance = distance
        if best_column is None:
            columns.append([block])
        else:
            best_column.append(block)

    columns.sort(key=lambda column: min(block.x0 for block in column))
    result: list[TextBlock] = []
    for column in columns:
        result.extend(sorted(column, key=lambda block: (block.y0, block.x0)))
    return result


def _to_text_block(item: Any) -> TextBlock | None:
    if not isinstance(item, (tuple, list)) or len(item) < 5:
        return None
    if len(item) >= 7 and item[6] not in (0, None):
        return None
    try:
        return TextBlock(
            x0=float(item[0]),
            y0=float(item[1]),
            x1=float(item[2]),
            y1=float(item[3]),
            text=str(item[4] or ""),
        )
    except (TypeError, ValueError):
        return None


def _page_width(page: Any, raw_blocks: Iterable[Any]) -> float:
    try:
        width = float(page.rect.width)
        if width > 0:
            return width
    except Exception:
        pass
    x_values = []
    for item in raw_blocks:
        if isinstance(item, (tuple, list)) and len(item) >= 3:
            try:
                x_values.append(float(item[2]))
            except (TypeError, ValueError):
                continue
    return max(x_values, default=1.0)
