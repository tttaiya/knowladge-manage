from __future__ import annotations

from typing import List, Tuple

from app.schemas.retrieval import (
    EmbedChunk,
    EmbedRequest,
    EmbedResponse,
    EmbeddedChunk,
)
from app.services.vector_store import (
    ChromaStore,
    VectorStoreValidationError,
    build_vector_id,
)

from .client import EmbeddingClient


class EmbeddingServiceError(RuntimeError):
    """切片向量化业务处理失败。"""


class EmbeddingRequestError(EmbeddingServiceError):
    """Worker 传入的向量化请求不符合公共协议。"""


class EmbeddingService:
    """
    将切片正文转换为向量并写入 ChromaDB。

    处理流程：
    1. 校验 task 与 chunks；
    2. 生成或校验 vectorId；
    3. 调用 EmbeddingClient；
    4. 写入或覆盖 ChromaDB；
    5. 返回 Worker 可消费的结果。
    """

    def __init__(
        self,
        *,
        embedding_client: EmbeddingClient,
        vector_store: ChromaStore,
    ) -> None:
        self.embedding_client = embedding_client
        self.vector_store = vector_store

    def process(
        self,
        request: EmbedRequest,
    ) -> EmbedResponse:
        """处理初次 EMBED 或人工编辑后的 REEMBED。"""

        contexts = [
            self._resolve_chunk_context(
                request=request,
                chunk=chunk,
            )
            for chunk in request.chunks
        ]

        contents = [
            context[5]
            for context in contexts
        ]

        embeddings = self.embedding_client.embed_texts(
            contents
        )

        vector_ids: List[str] = []
        metadatas = []
        response_chunks: List[EmbeddedChunk] = []

        for chunk, context in zip(
            request.chunks,
            contexts,
        ):
            (
                doc_id,
                kb_id,
                version_no,
                chunk_index,
                vector_id,
                content,
            ) = context

            char_count = (
                chunk.char_count
                if chunk.char_count > 0
                else len(content)
            )

            metadata = {
                "docId": doc_id,
                "kbId": kb_id,
                "versionNo": version_no,
                "chunkIndex": chunk_index,
                "contentVersion": chunk.content_version,
                "vectorId": vector_id,
            }

            vector_ids.append(vector_id)
            metadatas.append(metadata)

            response_chunks.append(
                EmbeddedChunk(
                    chunkId=chunk.chunk_id,
                    docId=doc_id,
                    kbId=kb_id,
                    versionNo=version_no,
                    chunkIndex=chunk_index,
                    content=content,
                    chapterPath=chunk.chapter_path,
                    pageNo=chunk.page_no,
                    chunkType=chunk.chunk_type,
                    charCount=char_count,
                    vectorId=vector_id,
                    contentVersion=chunk.content_version,
                )
            )

        try:
            self.vector_store.upsert_chunks(
                vector_ids=vector_ids,
                embeddings=embeddings,
                documents=contents,
                metadatas=metadatas,
            )
        except VectorStoreValidationError:
            raise
        except Exception as exc:
            raise EmbeddingServiceError(
                f"向量写入 ChromaDB 失败：{exc}"
            ) from exc

        return EmbedResponse(
            success=True,
            docId=request.task.doc_id,
            vectorIds=vector_ids,
            chunks=response_chunks,
        )

    def _resolve_chunk_context(
        self,
        *,
        request: EmbedRequest,
        chunk: EmbedChunk,
    ) -> Tuple[int, int, int, int, str, str]:
        task = request.task

        doc_id = (
            chunk.doc_id
            if chunk.doc_id is not None
            else task.doc_id
        )
        kb_id = (
            chunk.kb_id
            if chunk.kb_id is not None
            else task.kb_id
        )
        version_no = (
            chunk.version_no
            if chunk.version_no is not None
            else task.target_version_no
        )
        chunk_index = chunk.chunk_index
        content = chunk.content.strip()

        if doc_id != task.doc_id:
            raise EmbeddingRequestError(
                "chunk.docId 与 task.docId 不一致"
            )

        if kb_id != task.kb_id:
            raise EmbeddingRequestError(
                "chunk.kbId 与 task.kbId 不一致"
            )

        expected_vector_id = build_vector_id(
            doc_id=doc_id,
            version_no=version_no,
            chunk_index=chunk_index,
        )

        if request.operation == "REEMBED":
            if not chunk.vector_id:
                raise EmbeddingRequestError(
                    "REEMBED 必须提供原 vectorId"
                )

            vector_id = chunk.vector_id.strip()

            if vector_id != expected_vector_id:
                raise EmbeddingRequestError(
                    "REEMBED 的 vectorId 不符合公共命名规则"
                )
        else:
            if (
                chunk.vector_id
                and chunk.vector_id.strip()
                != expected_vector_id
            ):
                raise EmbeddingRequestError(
                    "EMBED 请求中的 vectorId "
                    "与系统生成值不一致"
                )

            vector_id = expected_vector_id

        return (
            doc_id,
            kb_id,
            version_no,
            chunk_index,
            vector_id,
            content,
        )