from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import pytest

from app.schemas import TaskPayload
from app.services.parsers.parser_service import parse_document, parse_pdf, parse_xlsx


DATA_DIR = Path(__file__).resolve().parents[1] / "test-data" / "documents"


@pytest.mark.parametrize(
    ("filename", "module_name"),
    [
        ("normal-text.pdf", "fitz"),
        ("heading-sample.docx", "docx"),
        ("sample.pptx", "pptx"),
        ("sample.xlsx", "openpyxl"),
    ],
)
def test_real_document_formats_parse(filename, module_name):
    pytest.importorskip(module_name)
    path = DATA_DIR / filename
    text, blocks, extension = parse_document(
        str(path), path.suffix, TaskPayload(enableOcr=False)
    )
    assert extension == path.suffix.lstrip(".")
    assert text.strip()
    assert blocks
    assert all(block.char_count == len(block.content) for block in blocks)


@pytest.mark.parametrize("filename", ["plain-text.txt", "markdown-sample.md"])
def test_real_text_formats_parse(filename):
    path = DATA_DIR / filename
    text, blocks, _ = parse_document(str(path), path.suffix, TaskPayload())
    assert text.strip()
    assert blocks


def test_image_uses_ocr_and_returns_page_metadata():
    path = DATA_DIR / "ocr-image.png"
    with patch(
        "app.services.parsers.parser_service.ocr_image",
        return_value=("OCR 示例文本", 0.99),
    ):
        text, blocks, extension = parse_document(str(path), "png", TaskPayload())
    assert extension == "png"
    assert text == "OCR 示例文本"
    assert blocks[0].page_no == 1
    assert blocks[0].block_type == "ocr"


def test_pdf_page_limit_rejected_before_parsing_pages():
    class FakeDocument:
        page_count = 3

        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

    fake_fitz = SimpleNamespace(open=lambda _: FakeDocument())
    with patch.dict("sys.modules", {"fitz": fake_fitz}):
        with pytest.raises(ValueError, match="PDF 页数超过限制"):
            parse_pdf(Path("too-many-pages.pdf"), TaskPayload(maxPdfPages=2))


def test_xlsx_merged_cells_are_restored_without_changing_workbook(tmp_path):
    openpyxl = pytest.importorskip("openpyxl")
    path = tmp_path / "merged.xlsx"
    workbook = openpyxl.Workbook()
    sheet = workbook.active
    sheet.title = "人员"
    sheet.merge_cells("A1:C1")
    sheet["A1"] = "人员统计"
    sheet.append(["部门", "姓名", "工资"])
    sheet.merge_cells("A3:A4")
    sheet["A3"] = "研发部"
    sheet["B3"] = "张三"
    sheet["C3"] = 8000
    sheet["B4"] = "李四"
    sheet["C4"] = 9000
    workbook.save(path)
    workbook.close()

    blocks = parse_xlsx(path)

    assert len(blocks) == 1
    text = blocks[0].content
    assert "人员统计\t人员统计\t人员统计" in text
    assert "研发部\t张三\t8000" in text
    assert "研发部\t李四\t9000" in text


def test_pdf_automatically_ocrs_only_low_quality_page():
    class FakePixmap:
        def save(self, _):
            return None

    class FakePage:
        rect = SimpleNamespace(width=600)

        def __init__(self, text, has_image=False):
            self.text = text
            self.has_image = has_image

        def get_text(self, mode):
            if mode == "blocks":
                if not self.text:
                    return []
                return [(40, 40, 560, 100, self.text, 0, 0)]
            return self.text

        def get_images(self, full=True):
            return [(1,)] if self.has_image else []

        def get_pixmap(self, matrix, alpha=False):
            return FakePixmap()

    class FakeDocument:
        def __init__(self):
            self.pages = [
                FakePage("第一页包含足够多的正常文字，因此不应该调用 OCR。"),
                FakePage("", has_image=True),
            ]
            self.page_count = len(self.pages)

        def __iter__(self):
            return iter(self.pages)

        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

    fake_fitz = SimpleNamespace(
        open=lambda _: FakeDocument(),
        Matrix=lambda *_: object(),
    )
    with patch.dict("sys.modules", {"fitz": fake_fitz}), patch(
        "app.services.parsers.parser_service.ocr_image",
        return_value=("第二页由真实 OCR 适配器返回的识别文本。", 0.98),
    ) as mocked_ocr:
        blocks = parse_pdf(
            Path("hybrid.pdf"),
            TaskPayload(enableOcr=True, minPdfTextChars=10),
        )

    assert mocked_ocr.call_count == 1
    assert [block.page_no for block in blocks] == [1, 2]
    assert [block.block_type for block in blocks] == ["paragraph", "ocr"]
    assert "识别文本" in blocks[1].content
