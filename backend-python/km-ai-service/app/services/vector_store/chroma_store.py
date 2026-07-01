from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Any, Dict, List, Mapping, Optional, Sequence

import chromadb


DEFAULT_CHROMA_HOST = "localhost"
DEFAULT_CHROMA_PORT = 8001
DEFAULT_COLLECTION_NAME = "km_document_chunks"
DEFAULT_VECTOR_DIMENSION = 1024

REQUIRED_METADATA_KEYS = {
    "docId",
    "kbId",
    "versionNo",
    "chunkIndex",
    "contentVersion",
    "vectorId",
}


class VectorStoreError(RuntimeError):
    """ChromaDB 向量存储操作失败。"""


class VectorStoreValidationError(VectorStoreError):
    """传入向量、ID 或元数据不符合公共协议。"""


@dataclass(frozen=True)
class VectorSearchHit:
    """ChromaDB 返回的一条向量召回结果。"""

    vector_id: str
    distance: float
    similarity_score: float
    document: str
    metadata: Dict[str, Any]


def build_vector_id(
    doc_id: int,
    version_no: int,
    chunk_index: int,
) -> str:
    """
    按组内已确认的公共协议生成 vectorId。

    格式：
    doc_{docId}_v_{versionNo}_idx_{chunkIndex}
    """

    if doc_id < 1:
        raise VectorStoreValidationError(
            "docId 必须大于等于 1"
        )

    if version_no < 1:
        raise VectorStoreValidationError(
            "versionNo 必须大于等于 1"
        )

    if chunk_index < 1:
        raise VectorStoreValidationError(
            "chunkIndex 必须大于等于 1"
        )

    return (
        f"doc_{doc_id}"
        f"_v_{version_no}"
        f"_idx_{chunk_index}"
    )


class ChromaStore:
    """知识管理模块统一 ChromaDB Collection 适配器。"""

    def __init__(
        self,
        *,
        client: Optional[Any] = None,
        host: Optional[str] = None,
        port: Optional[int] = None,
        collection_name: Optional[str] = None,
        expected_dimension: int = DEFAULT_VECTOR_DIMENSION,
    ) -> None:
        if expected_dimension < 1:
            raise ValueError(
                "expected_dimension 必须大于等于 1"
            )

        self.expected_dimension = expected_dimension
        self.collection_name = (
            collection_name
            or os.getenv("CHROMA_COLLECTION")
            or DEFAULT_COLLECTION_NAME
        )

        if client is None:
            chroma_host = (
                host
                or os.getenv("CHROMA_HOST")
                or DEFAULT_CHROMA_HOST
            )

            env_port = os.getenv("CHROMA_PORT")
            chroma_port = (
                port
                if port is not None
                else int(env_port or DEFAULT_CHROMA_PORT)
            )

            self.client = chromadb.HttpClient(
                host=chroma_host,
                port=chroma_port,
            )
        else:
            self.client = client

        self.collection = self.client.get_or_create_collection(
            name=self.collection_name,
            metadata={
                "hnsw:space": "cosine",
            },
        )

    def count(self) -> int:
        """返回当前 Collection 的向量数量。"""

        return int(self.collection.count())

    def upsert_chunks(
        self,
        *,
        vector_ids: Sequence[str],
        embeddings: Sequence[Sequence[float]],
        documents: Sequence[str],
        metadatas: Sequence[Mapping[str, Any]],
    ) -> int:
        """批量新增或覆盖切片向量。"""

        item_count = len(vector_ids)

        if item_count == 0:
            return 0

        if len(set(vector_ids)) != item_count:
            raise VectorStoreValidationError(
                "同一批次中 vectorId 不允许重复"
            )

        if not (
            len(embeddings)
            == len(documents)
            == len(metadatas)
            == item_count
        ):
            raise VectorStoreValidationError(
                "vectorIds、embeddings、documents、"
                "metadatas 数量必须一致"
            )

        normalized_embeddings: List[List[float]] = []
        normalized_documents: List[str] = []
        normalized_metadatas: List[Dict[str, Any]] = []

        for index, vector_id in enumerate(vector_ids):
            normalized_vector_id = str(vector_id).strip()

            if not normalized_vector_id:
                raise VectorStoreValidationError(
                    "vectorId 不能为空"
                )

            embedding = embeddings[index]

            if len(embedding) != self.expected_dimension:
                raise VectorStoreValidationError(
                    "向量维度不正确："
                    f"期望 {self.expected_dimension}，"
                    f"实际 {len(embedding)}"
                )

            try:
                normalized_embedding = [
                    float(value)
                    for value in embedding
                ]
            except (TypeError, ValueError) as exc:
                raise VectorStoreValidationError(
                    "向量中包含非数值内容"
                ) from exc

            document = str(documents[index]).strip()

            if not document:
                raise VectorStoreValidationError(
                    "切片正文不能为空"
                )

            normalized_metadata = (
                self._normalize_metadata(
                    normalized_vector_id,
                    metadatas[index],
                )
            )

            normalized_embeddings.append(
                normalized_embedding
            )
            normalized_documents.append(document)
            normalized_metadatas.append(
                normalized_metadata
            )

        try:
            self.collection.upsert(
                ids=list(vector_ids),
                embeddings=normalized_embeddings,
                documents=normalized_documents,
                metadatas=normalized_metadatas,
            )
        except Exception as exc:
            raise VectorStoreError(
                f"ChromaDB 向量写入失败：{exc}"
            ) from exc

        return item_count

    def update_chunk(
        self,
        *,
        vector_id: str,
        embedding: Sequence[float],
        document: str,
        metadata: Mapping[str, Any],
    ) -> None:
        """
        使用原 vectorId 覆盖切片。

        人工编辑后的 REEMBED 不生成新 vectorId，
        而是使用 upsert 覆盖原记录。
        """

        self.upsert_chunks(
            vector_ids=[vector_id],
            embeddings=[embedding],
            documents=[document],
            metadatas=[metadata],
        )

    def search(
        self,
        *,
        query_embedding: Sequence[float],
        top_k: int,
        doc_ids: Optional[Sequence[int]] = None,
        kb_ids: Optional[Sequence[int]] = None,
    ) -> List[VectorSearchHit]:
        """按 query embedding 执行余弦向量召回。"""

        if top_k < 1:
            raise VectorStoreValidationError(
                "topK 必须大于等于 1"
            )

        if len(query_embedding) != self.expected_dimension:
            raise VectorStoreValidationError(
                "查询向量维度不正确："
                f"期望 {self.expected_dimension}，"
                f"实际 {len(query_embedding)}"
            )

        total = self.count()

        if total == 0:
            return []

        where = self._build_where_filter(
            doc_ids=doc_ids,
            kb_ids=kb_ids,
        )

        query_args: Dict[str, Any] = {
            "query_embeddings": [
                [float(value) for value in query_embedding]
            ],
            "n_results": min(top_k, total),
            "include": [
                "documents",
                "metadatas",
                "distances",
            ],
        }

        if where is not None:
            query_args["where"] = where

        try:
            result = self.collection.query(**query_args)
        except Exception as exc:
            raise VectorStoreError(
                f"ChromaDB 向量查询失败：{exc}"
            ) from exc

        result_ids = self._first_result_list(
            result.get("ids")
        )
        result_documents = self._first_result_list(
            result.get("documents")
        )
        result_metadatas = self._first_result_list(
            result.get("metadatas")
        )
        result_distances = self._first_result_list(
            result.get("distances")
        )

        hits: List[VectorSearchHit] = []

        for index, vector_id in enumerate(result_ids):
            distance = float(result_distances[index])
            similarity_score = 1.0 - distance

            document = (
                result_documents[index]
                if index < len(result_documents)
                else ""
            )

            metadata = (
                result_metadatas[index]
                if index < len(result_metadatas)
                else {}
            )

            hits.append(
                VectorSearchHit(
                    vector_id=str(vector_id),
                    distance=distance,
                    similarity_score=similarity_score,
                    document=str(document or ""),
                    metadata=dict(metadata or {}),
                )
            )

        return hits

    def delete_document(self, doc_id: int) -> int:
        """删除指定文档的所有版本向量。"""

        if doc_id < 1:
            raise VectorStoreValidationError(
                "docId 必须大于等于 1"
            )

        return self._delete_by_where(
            {
                "docId": int(doc_id),
            }
        )

    def delete_document_version(
        self,
        doc_id: int,
        version_no: int,
    ) -> int:
        """删除指定文档的指定版本向量。"""

        if doc_id < 1:
            raise VectorStoreValidationError(
                "docId 必须大于等于 1"
            )

        if version_no < 1:
            raise VectorStoreValidationError(
                "versionNo 必须大于等于 1"
            )

        return self._delete_by_where(
            {
                "$and": [
                    {
                        "docId": int(doc_id),
                    },
                    {
                        "versionNo": int(version_no),
                    },
                ]
            }
        )

    def _delete_by_where(
        self,
        where: Mapping[str, Any],
    ) -> int:
        try:
            records = self.collection.get(
                where=dict(where),
            )
            vector_ids = list(
                records.get("ids") or []
            )

            if vector_ids:
                self.collection.delete(
                    ids=vector_ids,
                )

            return len(vector_ids)
        except Exception as exc:
            raise VectorStoreError(
                f"ChromaDB 向量删除失败：{exc}"
            ) from exc

    def _normalize_metadata(
        self,
        vector_id: str,
        metadata: Mapping[str, Any],
    ) -> Dict[str, Any]:
        missing_keys = (
            REQUIRED_METADATA_KEYS
            - set(metadata.keys())
        )

        if missing_keys:
            missing_text = ", ".join(
                sorted(missing_keys)
            )
            raise VectorStoreValidationError(
                f"Chroma metadata 缺少字段：{missing_text}"
            )

        if str(metadata["vectorId"]) != vector_id:
            raise VectorStoreValidationError(
                "metadata.vectorId 必须与 recordId 一致"
            )

        for key in (
            "docId",
            "kbId",
            "versionNo",
            "chunkIndex",
            "contentVersion",
        ):
            value = metadata[key]

            if not isinstance(value, int) or value < 1:
                raise VectorStoreValidationError(
                    f"metadata.{key} 必须是大于等于 1 的整数"
                )

        normalized: Dict[str, Any] = {}

        for key, value in metadata.items():
            if value is None:
                continue

            if not isinstance(
                value,
                (str, int, float, bool),
            ):
                raise VectorStoreValidationError(
                    f"metadata.{key} 不是 Chroma 支持的标量类型"
                )

            normalized[str(key)] = value

        return normalized

    @staticmethod
    def _build_where_filter(
        *,
        doc_ids: Optional[Sequence[int]],
        kb_ids: Optional[Sequence[int]],
    ) -> Optional[Dict[str, Any]]:
        filters: List[Dict[str, Any]] = []

        normalized_doc_ids = sorted(
            {
                int(doc_id)
                for doc_id in (doc_ids or [])
            }
        )
        normalized_kb_ids = sorted(
            {
                int(kb_id)
                for kb_id in (kb_ids or [])
            }
        )

        if normalized_doc_ids:
            filters.append(
                {
                    "docId": {
                        "$in": normalized_doc_ids,
                    }
                }
            )

        if normalized_kb_ids:
            filters.append(
                {
                    "kbId": {
                        "$in": normalized_kb_ids,
                    }
                }
            )

        if not filters:
            return None

        if len(filters) == 1:
            return filters[0]

        return {
            "$and": filters,
        }

    @staticmethod
    def _first_result_list(
        value: Optional[Sequence[Sequence[Any]]],
    ) -> List[Any]:
        if not value:
            return []

        first = value[0]

        if first is None:
            return []

        return list(first)