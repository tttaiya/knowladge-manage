from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from app.schemas import TaskPayload
from app.services.parsers.parser_service import parse_pdf
from app.services.parsers.text_utils import (
    clean_document_text,
    clean_pdf_pages,
    clean_text,
    repair_soft_line_breaks,
)


def test_dirty_characters_and_consecutive_duplicate_lines_are_cleaned():
    dirty = (
        "\ufeff知 识 管 理\u200b\x00\ufffd\n"
        "重复内容段落\n"
        "重复内容段落\n\n"
        "正常正文"
    )

    cleaned = clean_document_text(dirty)

    assert "知识管理" in cleaned
    assert "\u200b" not in cleaned
    assert "\x00" not in cleaned
    assert "\ufffd" not in cleaned
    assert cleaned.count("重复内容段落") == 1
    assert "正常正文" in cleaned


def test_table_tabs_are_not_treated_as_spaced_ocr_text():
    assert clean_text("中\t华\t人\t民") == "中\t华\t人\t民"


def test_pdf_repeated_header_footer_and_page_numbers_are_removed():
    pages = [
        "知识管理项目报告\n第一页正文内容完整。\n第 1 页",
        "知识管理项目报告\n第二页正文内容完整。\n第 2 页",
        "知识管理项目报告\n第三页正文内容完整。\n第 3 页",
    ]

    cleaned = clean_pdf_pages(pages)

    assert all("知识管理项目报告" not in page for page in cleaned)
    assert all("第 1 页" not in page and "第 2 页" not in page and "第 3 页" not in page for page in cleaned)
    assert "第一页正文内容完整。" in cleaned[0]
    assert "第二页正文内容完整。" in cleaned[1]
    assert "第三页正文内容完整。" in cleaned[2]


def test_repeated_top_lines_in_two_page_document_are_preserved():
    cleaned = clean_pdf_pages(
        ["共同标题\n第一页正文。", "共同标题\n第二页正文。"]
    )

    assert all("共同标题" in page for page in cleaned)


def test_soft_line_breaks_are_repaired_without_flattening_lists():
    dirty = (
        "这是被 PDF 错误换行的\n中文句子。\n\n"
        "inter-\nnational standard.\n\n"
        "一、适用范围\n列表正文。"
    )

    cleaned = repair_soft_line_breaks(dirty)

    assert "这是被 PDF 错误换行的中文句子。" in cleaned
    assert "international standard." in cleaned
    assert "一、适用范围\n列表正文。" in cleaned


def test_parse_pdf_applies_page_level_cleaning_before_returning_blocks():
    class FakePage:
        def __init__(self, text: str):
            self.text = text

        def get_text(self, _: str) -> str:
            return self.text

    class FakeDocument:
        def __init__(self):
            self.pages = [
                FakePage("内部资料\n第一页正文。\n1"),
                FakePage("内部资料\n第二页正文。\n2"),
                FakePage("内部资料\n第三页正文。\n3"),
            ]
            self.page_count = len(self.pages)

        def __iter__(self):
            return iter(self.pages)

        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

    fake_fitz = SimpleNamespace(open=lambda _: FakeDocument())
    with patch.dict("sys.modules", {"fitz": fake_fitz}):
        blocks = parse_pdf(
            Path("dirty.pdf"),
            TaskPayload(enableOcr=False, minPdfTextChars=1),
        )

    assert len(blocks) == 3
    assert all("内部资料" not in block.content for block in blocks)
    assert [block.page_no for block in blocks] == [1, 2, 3]
    assert all(block.char_count == len(block.content) for block in blocks)
