"""Authenticated chat APIs with server-side persistence."""

import json
import time
import asyncio

from fastapi import APIRouter, Depends, HTTPException
from sse_starlette.sse import EventSourceResponse
from sqlalchemy.orm import Session

from app.auth.permissions import require_login
from app.db.session import get_db
from app.models.orm.user import User
from app.models.request import ChatRequest, ClearRequest
from app.models.response import ApiResponse, SessionInfoResponse
from app.repositories.conversation_repository import ConversationRepository
from app.repositories.metrics_repository import MetricsRepository
from app.services.citation_service import CitationService
from app.services.conversation_service import ConversationService
from app.services.general_chat_service import GeneralChatService
from app.services.intent_service import IntentService
from app.services.rag_agent_service import rag_agent_service
from app.services.rag_pipeline_service import RagPipelineService
from app.services.settings_service import SettingsService

router = APIRouter()


def sse_event(event_type: str, **data) -> dict:
    return {"event": "message", "data": json.dumps({"type": event_type, "data": data}, ensure_ascii=False)}


def record_process_step(process_steps: list[dict], node: str, status: str, message: str) -> None:
    if not node:
        return
    step = {
        "node": str(node),
        "status": str(status or "running"),
        "message": str(message or ""),
    }
    for item in process_steps:
        if item.get("node") == step["node"]:
            item.update(step)
            return
    process_steps.append(step)


def build_token_usage(existing: dict | None, process_steps: list[dict]) -> dict:
    payload = dict(existing) if isinstance(existing, dict) else {}
    payload["process_steps"] = [
        {
            "node": str(step.get("node") or ""),
            "status": str(step.get("status") or "running"),
            "message": str(step.get("message") or ""),
        }
        for step in process_steps
        if isinstance(step, dict) and step.get("node")
    ]
    return payload


def extract_process_steps(token_usage: dict | None) -> list[dict]:
    if not isinstance(token_usage, dict):
        return []
    steps = token_usage.get("process_steps")
    if not isinstance(steps, list):
        return []
    normalized: list[dict] = []
    for item in steps:
        if not isinstance(item, dict) or not item.get("node"):
            continue
        normalized.append(
            {
                "node": str(item.get("node")),
                "status": str(item.get("status") or "running"),
                "message": str(item.get("message") or ""),
            }
        )
    return normalized


async def stream_text_chunks(text: str, size: int = 18):
    for index in range(0, len(text), size):
        yield text[index : index + size]
        await asyncio.sleep(0.015)


async def _run_chat(request: ChatRequest, user: User, db: Session) -> dict:
    started = time.perf_counter()
    conversations = ConversationService(db)
    session, created = conversations.ensure_session(user.id, request.id, request.question)
    user_message = conversations.add_message(user.id, session.id, "user", request.question or "")
    assistant = conversations.add_message(user.id, session.id, "assistant", "", status="streaming")
    intent = IntentService(db).detect(request.question or "", request.mode)
    process_steps: list[dict] = []
    record_process_step(process_steps, "intent_detection", "success", intent.reason or "已完成意图识别")
    conversations.update_message(user_message, intent=intent.intent)
    db.commit()
    knowledge_base_ids = request.knowledge_base_ids or SettingsService(db).get("rag.bound_knowledge_base_ids", [])

    try:
        if intent.intent == "knowledge_qa":
            rag_result = await RagPipelineService(db).answer(request.question or "", knowledge_base_ids)
            answer = rag_result.answer
            citations = rag_result.citations
            grounded = rag_result.is_knowledge_grounded
            no_evidence = rag_result.no_evidence
            record_process_step(process_steps, "terminology_matching", "success", "已完成术语匹配")
            record_process_step(
                process_steps,
                "knowledge_retrieval",
                "warning" if no_evidence else "success",
                "当前知识库未检索到足够依据" if no_evidence else "已完成知识库检索",
            )
            record_process_step(
                process_steps,
                "answer_generation",
                "success",
                "答案生成完成" if grounded else "答案生成完成，已标记为非知识库依据",
            )
        else:
            answer = await GeneralChatService(db).answer(request.question or "")
            citations = []
            grounded = False
            no_evidence = False
            record_process_step(process_steps, "llm_call", "success", "大模型调用完成")
            record_process_step(process_steps, "answer_generation", "success", "答案生成完成")
    except Exception as exc:
        record_process_step(process_steps, "answer_generation", "failed", str(exc) or "生成失败")
        conversations.update_message(
            assistant,
            status="failed",
            intent=intent.intent,
            error_message=str(exc),
            token_usage=build_token_usage(assistant.token_usage, process_steps),
            latency_ms=int((time.perf_counter() - started) * 1000),
        )
        MetricsRepository(db).record(
            user_id=user.id,
            session_id=session.id,
            message_id=assistant.id,
            question=request.question or "",
            intent=intent.intent,
            hit_knowledge=False,
            knowledge_base_ids=knowledge_base_ids,
            citation_count=0,
            latency_ms=assistant.latency_ms or 0,
            status="failed",
            error_type="CHAT_FAILED",
        )
        db.commit()
        raise

    conversations.update_message(
        assistant,
        content=answer,
        status="completed",
        intent=intent.intent,
        is_knowledge_grounded=grounded,
        token_usage=build_token_usage(assistant.token_usage, process_steps),
        latency_ms=int((time.perf_counter() - started) * 1000),
    )
    CitationService(db).persist(assistant.id, citations)
    MetricsRepository(db).record(
        user_id=user.id,
        session_id=session.id,
        message_id=assistant.id,
        question=request.question or "",
        intent=intent.intent,
        hit_knowledge=grounded,
        knowledge_base_ids=knowledge_base_ids,
        citation_count=len(citations),
        latency_ms=assistant.latency_ms or 0,
        status="success",
    )
    db.commit()

    return {
        "session": session,
        "session_created": created,
        "user_message": user_message,
        "assistant_message": assistant,
        "intent": intent,
        "answer": answer,
        "citations": citations,
            "grounded": grounded,
        "no_evidence": no_evidence,
        "knowledge_base_ids": knowledge_base_ids,
    }


@router.post("/chat")
async def chat(request: ChatRequest, user: User = Depends(require_login), db: Session = Depends(get_db)):
    try:
        result = await _run_chat(request, user, db)
        return {
            "code": 200,
            "message": "success",
            "data": {
                "success": True,
                "session_id": result["session"].id,
                "message_id": result["assistant_message"].id,
                "answer": result["answer"],
                "citations": result["citations"],
                "is_knowledge_grounded": result["grounded"],
                "no_evidence": result["no_evidence"],
                "intent": {
                    "intent": result["intent"].intent,
                    "confidence": result["intent"].confidence,
                    "reason": result["intent"].reason,
                },
                "errorMessage": None,
            },
        }
    except HTTPException:
        raise
    except Exception as exc:
        db.rollback()
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@router.post("/chat_stream")
async def chat_stream(request: ChatRequest, user: User = Depends(require_login), db: Session = Depends(get_db)):
    async def event_generator():
        started = time.perf_counter()
        conversations = ConversationService(db)
        session = None
        assistant = None
        intent = None
        answer = ""
        citations = []
        grounded = False
        process_steps: list[dict] = []

        def emit_node_status(node: str, status: str, message: str, **extra) -> dict:
            record_process_step(process_steps, node, status, message)
            return sse_event("node_status", node=node, status=status, message=message, **extra)

        try:
            yield emit_node_status("intent_detection", "running", "正在识别问题类型")

            session, created = conversations.ensure_session(user.id, request.id, request.question)
            user_message = conversations.add_message(user.id, session.id, "user", request.question or "")
            assistant = conversations.add_message(user.id, session.id, "assistant", "", status="streaming")
            intent = IntentService(db).detect(request.question or "", request.mode)
            conversations.update_message(user_message, intent=intent.intent)
            db.commit()
            knowledge_base_ids = request.knowledge_base_ids or SettingsService(db).get("rag.bound_knowledge_base_ids", [])

            if created:
                yield sse_event("session_created", session_id=session.id)
            yield sse_event("message_created", user_message_id=user_message.id, assistant_message_id=assistant.id)
            yield emit_node_status(
                "intent_detection",
                "success",
                intent.reason,
                intent=intent.intent,
                confidence=intent.confidence,
            )

            if intent.intent == "knowledge_qa":
                yield emit_node_status("terminology_matching", "running", "正在匹配电力术语与规范关键词")
                yield emit_node_status("terminology_matching", "success", "已完成术语匹配")
                yield emit_node_status("knowledge_retrieval", "running", "正在召回知识库片段")
                rag_result = await RagPipelineService(db).answer(request.question or "", knowledge_base_ids)
                answer = rag_result.answer
                citations = rag_result.citations
                grounded = rag_result.is_knowledge_grounded
                retrieval_status = "warning" if rag_result.no_evidence else "success"
                retrieval_message = "当前知识库未检索到足够依据" if rag_result.no_evidence else "已完成知识库检索"
                yield emit_node_status("knowledge_retrieval", retrieval_status, retrieval_message)
                yield sse_event("citation_created", citations=citations)
                yield emit_node_status("answer_generation", "running", "正在生成答案")
                yield emit_node_status(
                    "answer_generation",
                    "success",
                    "答案生成完成" if grounded else "答案生成完成，已标记为非知识库依据",
                    knowledge_grounded=grounded,
                )
                async for text in stream_text_chunks(answer):
                    yield sse_event("content", text=text)
            else:
                yield emit_node_status("llm_call", "running", "正在调用大模型")
                yield emit_node_status("answer_generation", "running", "正在生成答案")
                async for chunk in rag_agent_service.query_stream(request.question or "", session_id=session.id):
                    chunk_type = chunk.get("type")
                    if chunk_type == "content":
                        text = chunk.get("data") or ""
                        answer += text
                    elif chunk_type == "error":
                        raise RuntimeError(chunk.get("data") or "模型调用失败")
                yield emit_node_status("llm_call", "success", "大模型调用完成")
                yield emit_node_status("answer_generation", "success", "答案生成完成")
                async for text in stream_text_chunks(answer):
                    yield sse_event("content", text=text)

            conversations.update_message(
                assistant,
                content=answer,
                status="completed",
                intent=intent.intent,
                is_knowledge_grounded=grounded,
                token_usage=build_token_usage(assistant.token_usage, process_steps),
                latency_ms=int((time.perf_counter() - started) * 1000),
            )
            CitationService(db).persist(assistant.id, citations)
            MetricsRepository(db).record(
                user_id=user.id,
                session_id=session.id,
                message_id=assistant.id,
                question=request.question or "",
                intent=intent.intent,
                hit_knowledge=grounded,
                knowledge_base_ids=knowledge_base_ids,
                citation_count=len(citations),
                latency_ms=assistant.latency_ms or 0,
                status="success",
            )
            db.commit()
            yield sse_event("done", answer=answer, citations=citations, message_id=assistant.id)
        except Exception as exc:
            db.rollback()
            if assistant is not None:
                try:
                    record_process_step(process_steps, "answer_generation", "failed", str(exc) or "生成失败")
                    conversations.update_message(
                        assistant,
                        content=answer,
                        status="failed",
                        intent=intent.intent if intent else None,
                        error_message=str(exc),
                        token_usage=build_token_usage(assistant.token_usage, process_steps),
                        latency_ms=int((time.perf_counter() - started) * 1000),
                    )
                    MetricsRepository(db).record(
                        user_id=user.id,
                        session_id=session.id if session else request.id or "",
                        message_id=assistant.id,
                        question=request.question or "",
                        intent=intent.intent if intent else None,
                        hit_knowledge=False,
                        citation_count=0,
                        latency_ms=assistant.latency_ms or 0,
                        status="failed",
                        error_type="CHAT_STREAM_FAILED",
                    )
                    db.commit()
                except Exception:
                    db.rollback()
            yield sse_event("error", error_type="CHAT_FAILED", message=str(exc), retryable=True)
        except asyncio.CancelledError:
            db.rollback()
            if assistant is not None:
                try:
                    record_process_step(process_steps, "answer_generation", "warning", "已停止生成")
                    conversations.update_message(
                        assistant,
                        content=answer,
                        status="stopped",
                        intent=intent.intent if intent else None,
                        token_usage=build_token_usage(assistant.token_usage, process_steps),
                        latency_ms=int((time.perf_counter() - started) * 1000),
                    )
                    MetricsRepository(db).record(
                        user_id=user.id,
                        session_id=session.id if session else request.id or "",
                        message_id=assistant.id,
                        question=request.question or "",
                        intent=intent.intent if intent else None,
                        hit_knowledge=grounded,
                        citation_count=len(citations),
                        latency_ms=assistant.latency_ms or 0,
                        status="stopped",
                    )
                    db.commit()
                except Exception:
                    db.rollback()
            raise

    return EventSourceResponse(event_generator())


@router.post("/chat/{message_id}/stop")
async def stop_chat(message_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)):
    repo = ConversationRepository(db)
    message = repo.get_message(user.id, message_id)
    if not message:
        raise HTTPException(status_code=404, detail="消息不存在")
    repo.update_message(message, status="stopped")
    MetricsRepository(db).record(
        user_id=user.id,
        session_id=message.session_id,
        message_id=message.id,
        question="",
        intent=message.intent,
        hit_knowledge=message.is_knowledge_grounded,
        citation_count=0,
        latency_ms=message.latency_ms or 0,
        status="stopped",
    )
    db.commit()
    return {"code": 200, "message": "success", "data": {"message_id": message.id, "status": message.status}}


@router.post("/chat/regenerate")
async def regenerate(payload: dict, user: User = Depends(require_login), db: Session = Depends(get_db)):
    user_message_id = payload.get("user_message_id")
    repo = ConversationRepository(db)
    message = repo.get_message(user.id, user_message_id)
    if not message or message.role != "user":
        raise HTTPException(status_code=404, detail="用户消息不存在")
    request = ChatRequest(session_id=message.session_id, question=message.content, mode=payload.get("mode", "auto"))
    return await chat(request, user, db)


@router.post("/chat/clear", response_model=ApiResponse)
async def clear_session(request: ClearRequest, user: User = Depends(require_login), db: Session = Depends(get_db)):
    ConversationService(db).delete_session(user.id, request.session_id)
    return ApiResponse(status="success", message="会话已清空", data=None)


@router.get("/chat/session/{session_id}", response_model=SessionInfoResponse)
async def get_session_info(session_id: str, user: User = Depends(require_login), db: Session = Depends(get_db)) -> SessionInfoResponse:
    messages = ConversationService(db).list_messages(user.id, session_id)
    citation_map = CitationService(db).list_for_messages([row.id for row in messages if row.role == "assistant"])
    return SessionInfoResponse(
        session_id=session_id,
        message_count=len(messages),
        history=[
            {
                "id": row.id,
                "role": row.role,
                "content": row.content,
                "created_at": row.created_at.isoformat() if row.created_at else None,
                "is_knowledge_grounded": row.is_knowledge_grounded,
                "citations": citation_map.get(row.id, []),
                "process_steps": extract_process_steps(row.token_usage),
            }
            for row in messages
        ],
    )
