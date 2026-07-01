from app.schemas.common import parse_task_payload


def test_knowledge_base_snapshot_maps_to_f4_chunk_settings():
    payload = parse_task_payload(
        {
            "knowledgeBaseSnapshot": {
                "chunkStrategy": "HEADING",
                "chunkSize": 50,
                "chunkOverlap": 10,
                "separators": ["\n\n", "。"],
            }
        }
    )
    assert payload.chunk_mode == "heading"
    assert payload.chunk_size == 50
    assert payload.overlap == 10
    assert payload.separator == ["\n\n", "。"]


def test_top_level_f4_settings_override_snapshot():
    payload = parse_task_payload(
        {
            "chunkMode": "fixed",
            "chunkSize": 200,
            "overlap": 20,
            "knowledgeBaseSnapshot": {
                "chunkStrategy": "HEADING",
                "chunkSize": 50,
                "chunkOverlap": 10,
            },
        }
    )
    assert payload.chunk_mode == "fixed"
    assert payload.chunk_size == 200
    assert payload.overlap == 20
