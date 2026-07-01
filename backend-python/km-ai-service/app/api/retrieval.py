from __future__ import annotations

import logging
from collections.abc import Generator

from fastapi import (
    APIRouter,
    Depends,
    HTTPException,
    Path,
    status,
)

from app.middleware import require_internal_token
from app.schemas.retrieval import (
    DeleteVectorsResponse,
    EmbedRequest,
    EmbedResponse,
    RetrievalSearchRequest,
)
from app.services.embeddings import (
    EmbeddingClient,
    EmbeddingService,
)
from app.services.rerank import RerankClient
from app.services.retrieval_service import RetrievalService
from app.services.vector_store import ChromaStore


logger = logging.getLogger(__name__)
router = APIRouter()


def get_embedding_service() -> Generator[
    EmbeddingService,
    None,
    None,
]:
    """
    创建真实向量化服务。

    配置来源：
    - Embedding 模型配置从环境变量读取；
    - ChromaDB 地址从环境变量读取；
    - 单元测试通过 dependency_overrides 替换为假服务。
    """

    embedding_client = EmbeddingClient()

    vector_store = ChromaStore(
        expected_dimension=embedding_client.dimension,
    )

    service = EmbeddingService(
        embedding_client=embedding_client,
        vector_store=vector_store,
    )

    try:
        yield service
    finally:
        embedding_client.close()


def get_retrieval_service() -> Generator[
    RetrievalService,
    None,
    None,
]:
    """
    创建真实向量检索与重排序服务。

    Java km-search-service 会先通过 MySQL 筛选：
    - READY 文档；
    - 未删除文档；
    - 符合知识库和标签要求的文档。

    FastAPI 只在 Java 提供的 docIds 范围内查询 ChromaDB。

    VECTOR_RERANK 模式下会调用 qwen3-rerank；
    调用失败时 RetrievalService 自动降级为 VECTOR_ONLY。
    """

    embedding_client = EmbeddingClient()
    rerank_client = RerankClient()

    vector_store = ChromaStore(
        expected_dimension=embedding_client.dimension,
    )

    service = RetrievalService(
        embedding_client=embedding_client,
        vector_store=vector_store,
        rerank_client=rerank_client,
    )

    try:
        yield service
    finally:
        embedding_client.close()
        rerank_client.close()


def get_vector_store() -> ChromaStore:
    """
    创建真实 ChromaDB 向量存储。

    删除接口不需要调用 Embedding 或 Rerank 模型，
    因此只创建 ChromaStore。
    """

    return ChromaStore()


@router.post(
    "/internal/ai/embed",
    dependencies=[Depends(require_internal_token)],
)
def embed_api(
    request: EmbedRequest,
    service: EmbeddingService = Depends(
        get_embedding_service
    ),
):
    """
    执行初次 EMBED 或人工编辑后的 REEMBED。

    成功时必须返回 success=true。
    Worker 会根据 success 字段判断任务是否成功。
    """

    try:
        response = service.process(request)

        return response.model_dump(
            by_alias=True,
            exclude_none=True,
        )

    except Exception as exc:
        logger.exception(
            "F5 embed failed traceId=%s taskId=%s "
            "docId=%s operation=%s",
            request.task.trace_id,
            request.task.task_id,
            request.task.doc_id,
            request.operation,
        )

        response = EmbedResponse(
            success=False,
            docId=request.task.doc_id,
            vectorIds=[],
            chunks=[],
            errorStage="EMBED",
            errorMessage=str(exc)[:500],
        )

        return response.model_dump(
            by_alias=True,
            exclude_none=True,
        )


@router.post(
    "/internal/ai/retrieval/search",
    dependencies=[Depends(require_internal_token)],
)
def retrieval_search_api(
    request: RetrievalSearchRequest,
    service: RetrievalService = Depends(
        get_retrieval_service
    ),
):
    """
    执行向量检索并返回 vectorId。

    Java Search 后续使用 vectorId 回查真实切片记录。
    检索服务真实故障时返回 HTTP 502。
    """

    try:
        response = service.search(request)

        return response.model_dump(
            by_alias=True,
            exclude_none=True,
        )

    except Exception as exc:
        logger.exception(
            "F5 retrieval failed query=%s "
            "kbIds=%s docCount=%s mode=%s",
            request.query[:100],
            request.knowledge_base_ids,
            len(request.doc_ids),
            request.mode,
        )

        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=(
                "向量检索服务调用失败："
                f"{str(exc)[:500]}"
            ),
        ) from exc


@router.delete(
    "/internal/ai/vectors/{doc_id}",
    dependencies=[Depends(require_internal_token)],
)
def delete_document_vectors_api(
    doc_id: int = Path(
        ...,
        ge=1,
        description="需要删除全部向量的文档 ID",
    ),
    vector_store: ChromaStore = Depends(
        get_vector_store
    ),
):
    """
    删除指定文档的所有版本向量。

    删除不存在的文档时：
    - success=true；
    - deletedCount=0。

    这样可以保证删除操作具有幂等性。
    """

    try:
        deleted_count = (
            vector_store.delete_document(doc_id)
        )

        response = DeleteVectorsResponse(
            success=True,
            docId=doc_id,
            deletedCount=deleted_count,
        )

    except Exception as exc:
        logger.exception(
            "F5 delete document vectors failed "
            "docId=%s",
            doc_id,
        )

        response = DeleteVectorsResponse(
            success=False,
            docId=doc_id,
            deletedCount=0,
            errorStage="CHROMA",
            errorMessage=str(exc)[:500],
        )

    return response.model_dump(
        by_alias=True,
        exclude_none=True,
    )


@router.delete(
    "/internal/ai/vectors/{doc_id}/versions/{version_no}",
    dependencies=[Depends(require_internal_token)],
)
def delete_document_version_vectors_api(
    doc_id: int = Path(
        ...,
        ge=1,
        description="文档 ID",
    ),
    version_no: int = Path(
        ...,
        ge=1,
        description="需要删除向量的版本号",
    ),
    vector_store: ChromaStore = Depends(
        get_vector_store
    ),
):
    """
    删除指定文档、指定版本的全部向量。

    不删除该文档其他版本的数据。
    """

    try:
        deleted_count = (
            vector_store.delete_document_version(
                doc_id,
                version_no,
            )
        )

        response = DeleteVectorsResponse(
            success=True,
            docId=doc_id,
            versionNo=version_no,
            deletedCount=deleted_count,
        )

    except Exception as exc:
        logger.exception(
            "F5 delete version vectors failed "
            "docId=%s versionNo=%s",
            doc_id,
            version_no,
        )

        response = DeleteVectorsResponse(
            success=False,
            docId=doc_id,
            versionNo=version_no,
            deletedCount=0,
            errorStage="CHROMA",
            errorMessage=str(exc)[:500],
        )

    return response.model_dump(
        by_alias=True,
        exclude_none=True,
    )