from app.schemas import ParsedBlock, TaskPayload
from app.services.chunkers import chunk_blocks
from app.services.chunkers.chunker_service import (
    chunk_quality_score,
    estimate_token_count,
    token_budget_for_size,
)


def test_fixed_chunk():
    blocks = [ParsedBlock(content="第一段内容。" * 100, pageNo=1, blockType="paragraph")]
    payload = TaskPayload(chunkMode="fixed", chunkSize=120, overlap=20, separator="。")
    chunks = chunk_blocks(blocks, payload)
    assert len(chunks) > 1
    assert chunks[0].chunk_index == 1
    assert all(len(chunk.content) <= 120 for chunk in chunks)


def test_heading_chunk():
    blocks = [
        ParsedBlock(
            content="第一章 总则\n这里是总则内容。\n一、适用范围\n这里是适用范围内容。\n1.1 检查要求\n这里是检查要求内容。",
            pageNo=1,
            blockType="paragraph",
        )
    ]
    payload = TaskPayload(chunkMode="heading", chunkSize=500, overlap=50, separator="\n")
    chunks = chunk_blocks(blocks, payload)
    assert len(chunks) >= 1
    assert any(chunk.chapter_path for chunk in chunks)


def test_adjacent_short_paragraphs_with_same_metadata_are_merged():
    blocks = [
        ParsedBlock(content="第一段很短。", chapterPath="第一章", blockType="paragraph"),
        ParsedBlock(content="第二段也很短。", chapterPath="第一章", blockType="paragraph"),
        ParsedBlock(content="第三段仍然很短。", chapterPath="第一章", blockType="paragraph"),
    ]
    payload = TaskPayload(chunkMode="fixed", chunkSize=100, overlap=0)

    chunks = chunk_blocks(blocks, payload)

    assert len(chunks) == 1
    assert "第一段很短" in chunks[0].content
    assert "第三段仍然很短" in chunks[0].content
    assert chunks[0].chapter_path == "第一章"


def test_different_pages_are_not_merged():
    blocks = [
        ParsedBlock(content="第一页内容。", pageNo=1, blockType="paragraph"),
        ParsedBlock(content="第二页内容。", pageNo=2, blockType="paragraph"),
    ]
    payload = TaskPayload(chunkMode="fixed", chunkSize=100, overlap=0)

    chunks = chunk_blocks(blocks, payload)

    assert len(chunks) == 2
    assert [chunk.page_no for chunk in chunks] == [1, 2]


def test_recursive_split_uses_multiple_separator_levels_and_stays_bounded():
    text = (
        "第一段包含多个句子。第一句用于测试。第二句继续补充。\n\n"
        "第二段没有特别长，但是会与前文一起超过目标大小；"
        "随后还包含逗号，继续验证递归降级，最后保证不会越界。"
    ) * 4
    blocks = [ParsedBlock(content=text, pageNo=1, blockType="paragraph")]
    payload = TaskPayload(
        chunkMode="fixed",
        chunkSize=90,
        overlap=15,
        separator=["\n\n", "\n", "。", "；", "，"],
    )

    chunks = chunk_blocks(blocks, payload)

    assert len(chunks) > 2
    assert all(0 < len(chunk.content) <= 90 for chunk in chunks)
    assert [chunk.chunk_index for chunk in chunks] == list(range(1, len(chunks) + 1))


def test_heading_mode_aggregates_paragraphs_in_same_section():
    blocks = [
        ParsedBlock(content="第一段。", chapterPath="第一章/背景", blockType="paragraph"),
        ParsedBlock(content="第二段。", chapterPath="第一章/背景", blockType="paragraph"),
    ]
    payload = TaskPayload(chunkMode="heading", chunkSize=100, overlap=0)

    chunks = chunk_blocks(blocks, payload)

    assert len(chunks) == 1
    assert chunks[0].chapter_path == "第一章/背景"
    assert "第一段" in chunks[0].content and "第二段" in chunks[0].content


def test_table_chunks_repeat_header_and_keep_rows_bounded():
    table = "名称\t数值\n" + "\n".join(
        f"指标{i}\t这是第{i}项较长的说明" for i in range(1, 13)
    )
    blocks = [ParsedBlock(content=table, pageNo=1, blockType="table")]
    payload = TaskPayload(chunkMode="fixed", chunkSize=80, overlap=10)

    chunks = chunk_blocks(blocks, payload)

    assert len(chunks) > 1
    assert all(chunk.content.startswith("名称\t数值") for chunk in chunks)
    assert all(len(chunk.content) <= 80 for chunk in chunks)
    assert all(chunk.chunk_type == "table" for chunk in chunks)


def test_list_items_are_not_split_when_single_item_fits():
    text = (
        "处理要求\n"
        "1. 第一项内容需要保持完整。\n"
        "2. 第二项内容同样保持完整。\n"
        "3. 第三项内容也不能从中间切开。"
    )
    blocks = [ParsedBlock(content=text, pageNo=1, blockType="paragraph")]
    payload = TaskPayload(chunkMode="fixed", chunkSize=55, overlap=10)

    chunks = chunk_blocks(blocks, payload)
    combined = "\n".join(chunk.content for chunk in chunks)

    assert len(chunks) >= 2
    assert all(len(chunk.content) <= 55 for chunk in chunks)
    for marker in ("1.", "2.", "3."):
        assert combined.count(marker) == 1


def test_ocr_uses_smaller_adaptive_chunks_than_normal_paragraphs():
    text = "这是用于验证自适应长度的OCR文本。" * 20
    payload = TaskPayload(chunkMode="fixed", chunkSize=100, overlap=0)

    paragraph_chunks = chunk_blocks(
        [ParsedBlock(content=text, pageNo=1, blockType="paragraph")], payload
    )
    ocr_chunks = chunk_blocks(
        [ParsedBlock(content=text, pageNo=1, blockType="ocr")], payload
    )

    assert max(len(chunk.content) for chunk in ocr_chunks) <= 80
    assert len(ocr_chunks) >= len(paragraph_chunks)


def test_token_estimate_is_kept_within_internal_budget():
    text = ("中文Token保护测试，包含大量标点！" * 30) + (" longword" * 30)
    blocks = [ParsedBlock(content=text, pageNo=1, blockType="paragraph")]
    payload = TaskPayload(chunkMode="fixed", chunkSize=90, overlap=12)

    chunks = chunk_blocks(blocks, payload)

    assert chunks
    assert all(len(chunk.content) <= 90 for chunk in chunks)
    assert all(
        estimate_token_count(chunk.content) <= token_budget_for_size(90)
        for chunk in chunks
    )


def test_quality_score_rewards_readable_complete_chunks():
    readable = "这是一段结构完整、内容清晰的正文，用于知识库检索。"
    noisy = "@@@###%%%^^^"

    assert chunk_quality_score(readable, 100) > chunk_quality_score(noisy, 100)
