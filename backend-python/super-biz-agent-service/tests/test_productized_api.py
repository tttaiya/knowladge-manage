"""API smoke tests for authentication and productized chat."""

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from fastapi.testclient import TestClient

from app.db.base import Base
from app.db.session import get_db
from app.main import app
from app.services.rag_pipeline_service import RagResult


def make_client():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(bind=engine)
    SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)

    def override_db():
        db = SessionLocal()
        try:
            yield db
        finally:
            db.close()

    app.dependency_overrides[get_db] = override_db
    return TestClient(app), engine


def test_auth_and_protected_admin_api():
    client, engine = make_client()
    try:
        assert client.get("/api/admin/dashboard/summary").status_code == 401

        response = client.post("/api/auth/register", json={"username": "alice", "password": "secret1"})
        assert response.status_code == 200

        response = client.post("/api/auth/login", json={"username": "alice", "password": "secret1"})
        assert response.status_code == 200
        token = response.json()["access_token"]

        response = client.get("/api/auth/me", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200
        assert response.json()["username"] == "alice"

        response = client.get("/api/admin/dashboard/summary", headers={"Authorization": f"Bearer {token}"})
        assert response.status_code == 200
        assert response.json()["user_count"] == 1
    finally:
        app.dependency_overrides.clear()
        Base.metadata.drop_all(bind=engine)
        engine.dispose()


def test_authenticated_chat_creates_server_session_and_metrics():
    client, engine = make_client()
    try:
        client.post("/api/auth/register", json={"username": "bob", "password": "secret1"})
        login = client.post("/api/auth/login", json={"username": "bob", "password": "secret1"}).json()
        headers = {"Authorization": f"Bearer {login['access_token']}"}

        assert client.post("/api/chat", json={"question": "你好"}).status_code == 401

        response = client.post("/api/chat", json={"question": "你好", "mode": "chat"}, headers=headers)
        assert response.status_code == 200
        data = response.json()["data"]
        assert data["session_id"]
        assert data["intent"]["intent"] == "general_chat"

        sessions = client.get("/api/conversations", headers=headers)
        assert sessions.status_code == 200
        assert len(sessions.json()) == 1

        summary = client.get("/api/admin/dashboard/summary", headers=headers).json()
        assert summary["qa_count"] == 1
    finally:
        app.dependency_overrides.clear()
        Base.metadata.drop_all(bind=engine)
        engine.dispose()


def test_session_history_and_citation_detail_include_message_metadata(monkeypatch):
    client, engine = make_client()
    try:
        client.post("/api/auth/register", json={"username": "carol", "password": "secret1"})
        login = client.post("/api/auth/login", json={"username": "carol", "password": "secret1"}).json()
        headers = {"Authorization": f"Bearer {login['access_token']}"}

        async def fake_answer(self, question, knowledge_base_ids=None):
            return RagResult(
                answer="依据见文档说明[1]",
                chunks=[],
                citations=[
                    {
                        "index": 1,
                        "knowledge_base_id": "kb-1",
                        "knowledge_base_name": "值班知识库",
                        "document_id": "doc-1",
                        "document_name": "service_unavailable.md",
                        "chunk_id": "chunk-1",
                        "section_path": "处置流程/服务恢复",
                        "content_preview": "先检查网关和上游依赖，再恢复服务。",
                        "vector_score": 0.9123,
                        "rerank_score": 0.8345,
                        "download_url": "/api/knowledge/documents/doc-1/download",
                    }
                ],
                is_knowledge_grounded=True,
                no_evidence=False,
            )

        monkeypatch.setattr("app.api.chat.RagPipelineService.answer", fake_answer)

        response = client.post(
            "/api/chat",
            json={"question": "服务不可用怎么处理？", "mode": "knowledge"},
            headers=headers,
        )
        assert response.status_code == 200
        payload = response.json()["data"]

        history = client.get(f"/api/chat/session/{payload['session_id']}", headers=headers)
        assert history.status_code == 200
        messages = history.json()["history"]
        assistant = next(item for item in messages if item["role"] == "assistant")
        assert assistant["id"] == payload["message_id"]
        assert assistant["citations"][0]["document_name"] == "service_unavailable.md"
        assert assistant["citations"][0]["download_url"] == "/api/knowledge/documents/doc-1/download"
        assert assistant["process_steps"] == [
            {"node": "intent_detection", "status": "success", "message": "用户强制选择知识库模式"},
            {"node": "terminology_matching", "status": "success", "message": "已完成术语匹配"},
            {"node": "knowledge_retrieval", "status": "success", "message": "已完成知识库检索"},
            {"node": "answer_generation", "status": "success", "message": "答案生成完成"},
        ]

        citations = client.get(f"/api/messages/{payload['message_id']}/citations", headers=headers)
        assert citations.status_code == 200
        citation = citations.json()["data"][0]
        assert citation["section_path"] == "处置流程/服务恢复"
        assert citation["content_preview"] == "先检查网关和上游依赖，再恢复服务。"
        assert citation["vector_score"] == 0.9123
        assert citation["rerank_score"] == 0.8345
    finally:
        app.dependency_overrides.clear()
        Base.metadata.drop_all(bind=engine)
        engine.dispose()
