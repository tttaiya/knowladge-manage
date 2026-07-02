from types import SimpleNamespace

from app.services.parsers.pdf_layout import extract_page_text, order_pdf_text_blocks


def test_two_column_blocks_are_sorted_by_column_then_vertical_position():
    blocks = [
        (320, 110, 560, 150, "右栏第一段", 3, 0),
        (40, 200, 280, 240, "左栏第二段", 2, 0),
        (40, 20, 560, 70, "跨栏标题", 0, 0),
        (320, 210, 560, 250, "右栏第二段", 4, 0),
        (40, 100, 280, 140, "左栏第一段", 1, 0),
    ]

    text = order_pdf_text_blocks(blocks, page_width=600)

    assert text.index("跨栏标题") < text.index("左栏第一段")
    assert text.index("左栏第一段") < text.index("左栏第二段")
    assert text.index("左栏第二段") < text.index("右栏第一段")
    assert text.index("右栏第一段") < text.index("右栏第二段")


def test_full_width_section_heading_is_inserted_between_column_regions():
    blocks = [
        (40, 80, 280, 110, "上半部分左栏", 0, 0),
        (320, 80, 560, 110, "上半部分右栏", 1, 0),
        (40, 180, 560, 220, "第二节", 2, 0),
        (40, 250, 280, 280, "下半部分左栏", 3, 0),
        (320, 250, 560, 280, "下半部分右栏", 4, 0),
    ]

    text = order_pdf_text_blocks(blocks, page_width=600)

    assert text.index("上半部分右栏") < text.index("第二节")
    assert text.index("第二节") < text.index("下半部分左栏")


def test_extract_page_text_ignores_image_blocks():
    class FakePage:
        rect = SimpleNamespace(width=600)

        def get_text(self, mode):
            if mode == "blocks":
                return [
                    (40, 20, 560, 70, "正文标题", 0, 0),
                    (0, 0, 600, 800, "图片占位", 1, 1),
                ]
            return "普通文本兜底"

    assert extract_page_text(FakePage()) == "正文标题"
