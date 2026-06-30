"""
F4 parse / chunk / process-document 路由（commit #22 加固版）。

加固项：
- R-F4-1 / R-F4-2：/internal/ai/* 全部加 require_internal_token 依赖
- R-F4-4 / R-F4-5 / R-F4-6 / R-F4-7：parse 入口加 resolve_safe_path
- R-F4-9：PARSE_OR_OCR 拆分为 PARSE / OCR / CHUNK / INTERNAL（具体阶段由 parse_document 内部抛异常阶段决定）
- R-F4-10：parse 入口和 chunk 入口加耗时日志（traceId/taskId/docId/extension/耗时/blockCount/chunkCount）
"""
from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends

from app.middleware import require_internal_token
from app.schemas import (
    ChunkRequest,
    ChunkResponse,
    ParseResponse,
    ProcessDocumentResponse,
    WorkerTaskRequest,
)
from app.services.chunkers import chunk_blocks
from app.services.parsers import parse_document
from app.services.path_guard import resolve_safe_path

logger = logging.getLogger(__name__)
router = APIRouter()


def as_response(model):
    """统一用 camelCase 返回给 Java Worker。"""
    return model.model_dump(by_alias=True, exclude_none=True)


@router.get("/health")
def health():
    return {"status": "UP", "service": "km-ai-service", "module": "parse-ocr-chunk"}


@router.post(
    "/internal/ai/parse",
    dependencies=[Depends(require_internal_token)],
)
def parse_api(request: WorkerTaskRequest):
    """正式对接接口 1：只做解析/OCR，返回 parsedText + blocks。"""
    start = time.time()
    logger.info(
        "F4 parse start traceId=%s taskId=%s docId=%s extension=%s filePath=%s",
        request.trace_id, request.task_id, request.doc_id, request.extension, request.file_path,
    )

    # R-F4-4 / R-F4-5 / R-F4-6 / R-F4-7：受限目录守卫
    try:
        safe_path = resolve_safe_path(request.file_path, request.extension)
    except Exception as exc:
        logger.warning(
            "F4 parse path-guard rejected traceId=%s err=%s", request.trace_id, exc,
        )
        return as_response(
            ParseResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=request.extension or "",
                parsedText="",
                blocks=[],
                errorStage="STAGING",
                errorMessage=str(exc),
            )
        )

    # 解析/OCR：parse_document 内部按异常类型分发 errorStage
    try:
        parsed_text, blocks, ext = parse_document(
            str(safe_path),
            request.extension,
            request.task_payload_json,
        )
        elapsed_ms = int((time.time() - start) * 1000)
        logger.info(
            "F4 parse OK traceId=%s taskId=%s elapsedMs=%d blockCount=%d",
            request.trace_id, request.task_id, elapsed_ms, len(blocks),
        )
        return as_response(
            ParseResponse(
                success=True,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=ext,
                parsedText=parsed_text,
                blocks=blocks,
            )
        )
    except FileNotFoundError as exc:
        return as_response(
            ParseResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=request.extension or "",
                parsedText="", blocks=[],
                errorStage="PARSE",
                errorMessage=f"file not found: {exc}",
            )
        )
    except ValueError as exc:
        # 格式不支持 / 参数非法 → PARSE
        return as_response(
            ParseResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=request.extension or "",
                parsedText="", blocks=[],
                errorStage="PARSE",
                errorMessage=str(exc),
            )
        )
    except Exception as exc:
        exc_name = exc.__class__.__name__
        # OCR 失败特征：paddleocr / paddleocr.PaddleOcr / paddle 相关异常
        if "Paddle" in exc_name or "OCR" in str(exc) or "ocr" in request.task_payload_json.parseBackend.lower():
            stage = "OCR"
        else:
            stage = "INTERNAL"
        logger.exception(
            "F4 parse failed traceId=%s taskId=%s stage=%s", request.trace_id, request.task_id, stage,
        )
        return as_response(
            ParseResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=request.extension or "",
                parsedText="", blocks=[],
                errorStage=stage,
                errorMessage=str(exc)[:500],
            )
        )


@router.post(
    "/internal/ai/chunk",
    dependencies=[Depends(require_internal_token)],
)
def chunk_api(request: ChunkRequest):
    """正式对接接口 2：只做切片，输入 blocks 或 parsedText，返回 chunks。"""
    start = time.time()
    logger.info(
        "F4 chunk start traceId=%s taskId=%s blockCount=%d hasText=%s",
        request.trace_id, request.task_id,
        len(request.blocks or []), bool(request.parsed_text),
    )

    try:
        blocks = request.blocks
        if not blocks and request.parsed_text:
            from app.schemas import ParsedBlock
            blocks = [
                ParsedBlock(
                    content=request.parsed_text,
                    pageNo=1,
                    blockType="paragraph",
                    chapterPath=None,
                    charCount=len(request.parsed_text),
                )
            ]
        if not blocks:
            raise ValueError("chunk 接口需要传 blocks 或 parsedText")

        chunks = chunk_blocks(blocks, request.task_payload_json)
        elapsed_ms = int((time.time() - start) * 1000)
        logger.info(
            "F4 chunk OK traceId=%s taskId=%s elapsedMs=%d chunkCount=%d",
            request.trace_id, request.task_id, elapsed_ms, len(chunks),
        )
        return as_response(
            ChunkResponse(
                success=True,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                chunks=chunks,
                chunkCount=len(chunks),
            )
        )
    except Exception as exc:
        logger.exception(
            "F4 chunk failed traceId=%s taskId=%s", request.trace_id, request.task_id,
        )
        return as_response(
            ChunkResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                chunks=[],
                chunkCount=0,
                errorStage="CHUNK",
                errorMessage=str(exc)[:500],
            )
        )


@router.post(
    "/internal/ai/process-document",
    dependencies=[Depends(require_internal_token)],
)
def process_document_api(request: WorkerTaskRequest):
    """本地调试兼容接口：parse + chunk 一次完成。正式 Worker 优先使用 parse/chunk 两个接口。"""
    try:
        safe_path = resolve_safe_path(request.file_path, request.extension)
    except Exception as exc:
        return as_response(
            ProcessDocumentResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=request.extension or "",
                parsedText="", blocks=[], chunks=[], chunkCount=0,
                errorStage="STAGING",
                errorMessage=str(exc),
            )
        )
    try:
        parsed_text, blocks, ext = parse_document(
            str(safe_path),
            request.extension,
            request.task_payload_json,
        )
        chunks = chunk_blocks(blocks, request.task_payload_json)
        return as_response(
            ProcessDocumentResponse(
                success=True,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=ext,
                parsedText=parsed_text,
                blocks=blocks,
                chunks=chunks,
                chunkCount=len(chunks),
            )
        )
    except Exception as exc:
        return as_response(
            ProcessDocumentResponse(
                success=False,
                taskId=request.task_id,
                docId=request.doc_id,
                kbId=request.kb_id,
                traceId=request.trace_id,
                extension=request.extension or "",
                parsedText="", blocks=[], chunks=[], chunkCount=0,
                errorStage="PROCESS_DOCUMENT",
                errorMessage=str(exc)[:500],
            )
        )