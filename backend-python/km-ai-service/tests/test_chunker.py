from app.schemas import ParsedBlock, TaskPayload
from app.services.chunkers import chunk_blocks


def test_fixed_chunk():
    blocks = [ParsedBlock(content="第一段内容。" * 100, pageNo=1, blockType="paragraph")]
    payload = TaskPayload(chunkMode="fixed", chunkSize=120, overlap=20, separator="。")
    chunks = chunk_blocks(blocks, payload)
    assert len(chunks) > 1
    assert chunks[0].chunk_index == 1


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
