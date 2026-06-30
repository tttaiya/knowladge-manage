import json
from datetime import datetime, timezone
from io import BytesIO


def test_context_compressor_extracts_structured_summary():
    from app.services.context_compressor import context_compressor

    long_log = "\n".join(
        [
            f"2026-06-02 10:{i:02d}:00 order-service ERROR timeout calling payment-service cpu {85 + i % 10}%"
            for i in range(80)
        ]
    )

    result = context_compressor.compress(long_log)

    assert result["compressed"] is True
    assert "structured_summary" in result
    assert result["structured_summary"]["key_errors"]
    assert result["structured_summary"]["metric_abnormalities"]
    assert "order-service" in result["summary_text"]


def test_bad_case_service_creates_and_reads_case(tmp_path):
    from app.models.bad_case import BadCaseCreateRequest
    from app.services.bad_case_service import BadCaseService

    service = BadCaseService(base_dir=str(tmp_path))
    record = service.create(
        BadCaseCreateRequest(
            trace_id="trace_1",
            case_type="workflow",
            symptom="Replanner ended too early",
            root_cause="insufficient decision criteria",
            fix_action="record replanner action and adjust prompt",
            verification_result="rerun passed",
        )
    )

    assert record.case_id.startswith("case_")
    assert service.get(record.case_id).root_cause == "insufficient decision criteria"
    assert service.list_cases()[0].trace_id == "trace_1"


def test_eval_cases_have_expected_distribution():
    path = "eval/agent_eval_cases.json"
    with open(path, encoding="utf-8") as f:
        cases = json.load(f)

    counts = {}
    for case in cases:
        counts[case["type"]] = counts.get(case["type"], 0) + 1
        assert case["id"]
        assert case["question"]
        assert case["expected_behavior"]

    assert len(cases) >= 12
    assert counts.get("rag", 0) >= 8
    assert counts.get("exception", 0) >= 4


def test_intent_service_routes_power_standard_questions_to_knowledge():
    from app.services.intent_service import IntentService

    result = IntentService().detect("DL/T 电力技术标准里对继电保护验收有哪些要求？")

    assert result.intent == "knowledge_qa"
    assert result.confidence > 0.8


def test_intent_service_routes_smalltalk_to_general_chat():
    from app.services.intent_service import IntentService

    result = IntentService().detect("你好，帮我讲个笑话")

    assert result.intent == "general_chat"


def test_rag_retrieve_filters_threshold_and_limits_each_knowledge_base():
    import asyncio

    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk

    rows = [
        RetrievedChunk(content="电力标准 a1", chunk_id="a1", knowledge_base_id="kb-a", normalized_score=0.9),
        RetrievedChunk(content="电力标准 a2", chunk_id="a2", knowledge_base_id="kb-a", normalized_score=0.8),
        RetrievedChunk(content="电力标准 b1", chunk_id="b1", knowledge_base_id="kb-b", normalized_score=0.7),
        RetrievedChunk(content="电力标准 b2", chunk_id="b2", knowledge_base_id="kb-b", normalized_score=0.3),
    ]
    service = RagPipelineService(vector_search=lambda question, top_k, kb_ids: rows, reranker=None)
    service._setting = lambda key, default: {
        "rag.vector_top_k": 1,
        "rag.vector_score_threshold": 0.5,
        "rag.retrieval_mode": "vector",
    }.get(key, default)

    chunks = asyncio.run(service.retrieve("电力标准", ["kb-a", "kb-b"]))

    assert [chunk.chunk_id for chunk in chunks] == ["a1", "b1"]


def test_rag_retrieve_applies_rerank_threshold_and_top_n():
    import asyncio

    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk
    from app.services.rerank.base import RerankResult

    class FakeReranker:
        async def rerank(self, query, chunks):
            return [
                RerankResult(chunk_id="c1", score=0.95),
                RerankResult(chunk_id="c2", score=0.4),
                RerankResult(chunk_id="c3", score=0.9),
            ]

    rows = [
        RetrievedChunk(content="电力标准 c1", chunk_id="c1", normalized_score=0.9),
        RetrievedChunk(content="电力标准 c2", chunk_id="c2", normalized_score=0.9),
        RetrievedChunk(content="电力标准 c3", chunk_id="c3", normalized_score=0.9),
    ]
    service = RagPipelineService(vector_search=lambda question, top_k, kb_ids: rows, reranker=FakeReranker())
    service._setting = lambda key, default: {
        "rag.vector_top_k": 3,
        "rag.vector_score_threshold": 0.0,
        "rag.retrieval_mode": "vector_rerank",
        "rag.rerank_score_threshold": 0.8,
        "rag.rerank_top_n": 1,
    }.get(key, default)

    chunks = asyncio.run(service.retrieve("电力标准"))

    assert [chunk.chunk_id for chunk in chunks] == ["c1"]
    assert chunks[0].rerank_score == 0.95


def test_rag_db_fallback_does_not_return_unmatched_chunks():
    import asyncio

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from app.db.base import Base
    from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument
    from app.models.orm.user import User
    from app.services.rag_pipeline_service import RagPipelineService

    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine)
    db = SessionLocal()
    try:
        user = User(username="rag-user", password_hash="hash", display_name="rag-user")
        db.add(user)
        db.flush()
        kb = KnowledgeBase(name="运维知识库", code="ops", created_by=user.id)
        db.add(kb)
        db.flush()
        doc = KnowledgeDocument(
            knowledge_base_id=kb.id,
            filename="memory.md",
            file_path="memory.md",
            file_ext="md",
            status="indexed",
            uploaded_by=user.id,
        )
        db.add(doc)
        db.flush()
        db.add(
            KnowledgeChunk(
                knowledge_base_id=kb.id,
                document_id=doc.id,
                section_path="全文",
                content="内存使用率过高会导致 OOM 和频繁 GC。",
                chunk_index=0,
            )
        )
        db.commit()

        service = RagPipelineService(db=db, vector_search=lambda question, top_k, kb_ids: [])
        chunks = asyncio.run(service.retrieve("DL/T 电力技术标准里对继电保护验收有哪些要求？", [kb.id]))

        assert chunks == []
    finally:
        db.close()
        engine.dispose()


def test_rag_vector_results_require_query_term_overlap():
    import asyncio

    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk

    rows = [
        RetrievedChunk(
            content="内存使用率过高会导致 OOM 和频繁 GC。",
            chunk_id="memory",
            normalized_score=0.99,
        )
    ]
    service = RagPipelineService(vector_search=lambda question, top_k, kb_ids: rows, reranker=None)
    service._setting = lambda key, default: {
        "rag.vector_top_k": 3,
        "rag.vector_score_threshold": 0.0,
        "rag.retrieval_mode": "vector",
        "rag.require_keyword_overlap": True,
    }.get(key, default)

    chunks = asyncio.run(service.retrieve("DL/T 电力技术标准里对继电保护验收有哪些要求？"))

    assert chunks == []


def test_upload_document_calls_vector_index_and_persists_vector_ids(monkeypatch):
    import asyncio

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from app.db.base import Base
    from app.models.orm.knowledge import KnowledgeChunk
    from app.models.orm.user import User
    from app.services.knowledge_base_service import KnowledgeBaseService
    from fastapi import UploadFile

    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine)
    db = SessionLocal()
    captured = {}

    def fake_index_chunks(chunks, document_name=""):
        captured["chunk_ids"] = [chunk.id for chunk in chunks]
        captured["document_name"] = document_name
        return {chunk.id: f"vec-{chunk.id}" for chunk in chunks}

    monkeypatch.setattr("app.services.knowledge_base_service.vector_index_service.index_chunks", fake_index_chunks)
    try:
        user = User(username="kb-user", password_hash="hash", display_name="kb-user")
        db.add(user)
        db.flush()
        kb = KnowledgeBaseService(db).create(user.id, "电力知识库", "power-kb")

        upload = UploadFile(
            filename="power.md",
            file=BytesIO(
                b"# \xe7\x94\xb5\xe5\x8a\x9b\xe5\xae\x89\xe5\x85\xa8\xe5\xb7\xa5\xe5\x99\xa8\xe5\x85\xb7\n"
                b"\xe7\x94\xb5\xe5\x8a\x9b\xe5\xae\x89\xe5\x85\xa8\xe5\xb7\xa5\xe5\x99\xa8\xe5\x85\xb7\xe5\x8c\x85\xe6\x8b\xac\xe7\xbb\x9d\xe7\xbc\x98\xe6\x89\x8b\xe5\xa5\x97\xe3\x80\x81\xe7\xbb\x9d\xe7\xbc\x98\xe9\x9d\xb4\xe3\x80\x81\xe9\xaa\x8c\xe7\x94\xb5\xe5\x99\xa8\xe3\x80\x82\n"
            ),
        )

        doc = asyncio.run(KnowledgeBaseService(db).upload_document(user.id, kb.id, upload))
        stored_chunks = db.query(KnowledgeChunk).filter(KnowledgeChunk.document_id == doc.id).all()

        assert captured["document_name"] == "power.md"
        assert captured["chunk_ids"]
        assert doc.chunk_count == len(stored_chunks)
        assert all(chunk.milvus_vector_id == f"vec-{chunk.id}" for chunk in stored_chunks)
    finally:
        db.close()
        engine.dispose()


def test_rag_prefers_vector_search_when_knowledge_base_bound():
    import asyncio

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from app.db.base import Base
    from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument
    from app.models.orm.user import User
    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk

    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine)
    db = SessionLocal()
    try:
        user = User(username="vector-user", password_hash="hash", display_name="vector-user")
        db.add(user)
        db.flush()
        kb = KnowledgeBase(name="电力知识库", code="power-vector", created_by=user.id)
        db.add(kb)
        db.flush()
        doc = KnowledgeDocument(
            knowledge_base_id=kb.id,
            filename="power.md",
            file_path="power.md",
            file_ext="md",
            status="indexed",
            uploaded_by=user.id,
        )
        db.add(doc)
        db.flush()
        chunk = KnowledgeChunk(
            knowledge_base_id=kb.id,
            document_id=doc.id,
            section_path="电力安全工器具",
            content="电力安全工器具包括绝缘手套、绝缘靴、验电器和绝缘杆。",
            chunk_index=0,
        )
        db.add(chunk)
        db.commit()

        service = RagPipelineService(
            db=db,
            vector_search=lambda question, top_k, kb_ids: [
                RetrievedChunk(
                    content="ignored",
                    chunk_id=chunk.id,
                    knowledge_base_id=kb.id,
                    normalized_score=0.88,
                    raw_score=0.12,
                    metadata={"retrieval_source": "milvus"},
                )
            ],
            reranker=None,
        )
        service._db_chunk_search = lambda *args, **kwargs: (_ for _ in ()).throw(AssertionError("should not fallback to db"))
        service._setting = lambda key, default: {
            "rag.vector_top_k": 3,
            "rag.vector_score_threshold": 0.0,
            "rag.retrieval_mode": "vector",
            "rag.require_keyword_overlap": False,
        }.get(key, default)

        chunks = asyncio.run(service.retrieve("安全工器具有哪些？", [kb.id]))

        assert len(chunks) == 1
        assert chunks[0].chunk_id == chunk.id
        assert chunks[0].document_name == "power.md"
    finally:
        db.close()
        engine.dispose()


def test_rag_falls_back_to_db_when_vector_search_returns_empty(monkeypatch):
    import asyncio

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from app.db.base import Base
    from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument
    from app.models.orm.user import User
    from app.services.rag_pipeline_service import RagPipelineService

    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine)
    db = SessionLocal()
    try:
        user = User(username="fallback-user", password_hash="hash", display_name="fallback-user")
        db.add(user)
        db.flush()
        kb = KnowledgeBase(name="电力知识库", code="power-db", created_by=user.id)
        db.add(kb)
        db.flush()
        doc = KnowledgeDocument(
            knowledge_base_id=kb.id,
            filename="switch.md",
            file_path="switch.md",
            file_ext="md",
            status="indexed",
            uploaded_by=user.id,
        )
        db.add(doc)
        db.flush()
        db.add(
            KnowledgeChunk(
                knowledge_base_id=kb.id,
                document_id=doc.id,
                section_path="倒闸操作",
                content="倒闸操作应执行唱票、复诵、监护制度。",
                chunk_index=0,
            )
        )
        db.commit()

        monkeypatch.setattr("app.services.rag_pipeline_service.vector_search_service.search_similar_documents", lambda *args, **kwargs: [])
        service = RagPipelineService(db=db, reranker=None)
        service._setting = lambda key, default: {
            "rag.vector_top_k": 3,
            "rag.vector_score_threshold": 0.0,
            "rag.retrieval_mode": "vector",
            "rag.require_keyword_overlap": False,
        }.get(key, default)

        chunks = asyncio.run(service.retrieve("倒闸操作应执行唱票、复诵、监护制度", [kb.id]))

        assert len(chunks) == 1
        assert chunks[0].section_path == "倒闸操作"
        assert chunks[0].metadata["retrieval_source"] == "db_fallback"
    finally:
        db.close()
        engine.dispose()


def test_rag_filters_soft_deleted_vector_hits():
    import asyncio

    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    from app.db.base import Base
    from app.models.orm.knowledge import KnowledgeBase, KnowledgeChunk, KnowledgeDocument
    from app.models.orm.user import User
    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk

    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine)
    db = SessionLocal()
    try:
        user = User(username="deleted-user", password_hash="hash", display_name="deleted-user")
        db.add(user)
        db.flush()
        kb = KnowledgeBase(name="电力知识库", code="power-deleted", created_by=user.id)
        db.add(kb)
        db.flush()
        doc = KnowledgeDocument(
            knowledge_base_id=kb.id,
            filename="deleted.md",
            file_path="deleted.md",
            file_ext="md",
            status="indexed",
            uploaded_by=user.id,
        )
        db.add(doc)
        db.flush()
        chunk = KnowledgeChunk(
            knowledge_base_id=kb.id,
            document_id=doc.id,
            section_path="电力安全工器具",
            content="绝缘杆用于高压操作。",
            chunk_index=0,
            deleted_at=datetime.now(timezone.utc),
        )
        db.add(chunk)
        db.commit()

        service = RagPipelineService(
            db=db,
            vector_search=lambda question, top_k, kb_ids: [
                RetrievedChunk(
                    content="stale",
                    chunk_id=chunk.id,
                    knowledge_base_id=kb.id,
                    normalized_score=0.95,
                    raw_score=0.05,
                    metadata={"retrieval_source": "milvus"},
                )
            ],
            reranker=None,
        )
        service._setting = lambda key, default: {
            "rag.vector_top_k": 3,
            "rag.vector_score_threshold": 0.0,
            "rag.retrieval_mode": "vector",
            "rag.require_keyword_overlap": False,
        }.get(key, default)

        chunks = asyncio.run(service.retrieve("绝缘杆用于什么场景", [kb.id]))

        assert chunks == []
    finally:
        db.close()
        engine.dispose()


def test_rag_answer_generates_with_llm_from_retrieved_chunks():
    import asyncio
    from types import SimpleNamespace

    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk

    class FakeLLM:
        def __init__(self):
            self.messages = None

        async def ainvoke(self, messages):
            self.messages = messages
            return SimpleNamespace(content="安全工器具包括绝缘杆、验电器和绝缘手套。[1]")

    rows = [
        RetrievedChunk(
            content="电力安全工器具包括绝缘杆、验电器和绝缘手套。",
            chunk_id="tools",
            document_name="电力测试知识库.md",
            section_path="电力安全工器具",
            normalized_score=0.99,
        )
    ]
    fake_llm = FakeLLM()
    service = RagPipelineService(
        vector_search=lambda question, top_k, kb_ids: rows,
        reranker=None,
        llm=fake_llm,
    )
    service._setting = lambda key, default: {
        "rag.vector_top_k": 3,
        "rag.vector_score_threshold": 0.0,
        "rag.retrieval_mode": "vector",
    }.get(key, default)

    result = asyncio.run(service.answer("告诉我电力安全工器具有哪些"))

    assert result.answer == "安全工器具包括绝缘杆、验电器和绝缘手套。[1]"
    assert result.citations[0]["section_path"] == "电力安全工器具"
    assert fake_llm.messages is not None
    assert "电力安全工器具包括绝缘杆" in fake_llm.messages[-1].content


def test_rag_answer_normalizes_duplicate_and_invalid_citations():
    import asyncio
    from types import SimpleNamespace

    from app.services.rag_pipeline_service import RagPipelineService, RetrievedChunk

    class FakeLLM:
        async def ainvoke(self, messages):
            return SimpleNamespace(content="结论来自第一段[1][1]，补充来自第二段[2][9]")

    rows = [
        RetrievedChunk(
            content="第一段依据。",
            chunk_id="chunk-1",
            document_name="依据A.md",
            normalized_score=0.99,
        ),
        RetrievedChunk(
            content="第一段依据（重复召回）。",
            chunk_id="chunk-1",
            document_name="依据A.md",
            normalized_score=0.98,
        ),
        RetrievedChunk(
            content="第二段依据。",
            chunk_id="chunk-2",
            document_name="依据B.md",
            normalized_score=0.97,
        ),
    ]
    service = RagPipelineService(
        vector_search=lambda question, top_k, kb_ids: rows,
        reranker=None,
        llm=FakeLLM(),
    )
    service._setting = lambda key, default: {
        "rag.vector_top_k": 3,
        "rag.vector_score_threshold": 0.0,
        "rag.retrieval_mode": "vector",
    }.get(key, default)
    service._has_query_term_overlap = lambda question, content: True

    result = asyncio.run(service.answer("请说明第一段依据和第二段依据"))

    assert result.answer == "结论来自第一段[1]，补充来自第二段[2]"
    assert [item["index"] for item in result.citations] == [1, 2]
    assert [item["chunk_id"] for item in result.citations] == ["chunk-1", "chunk-2"]
