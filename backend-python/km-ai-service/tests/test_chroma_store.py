from __future__ import annotations

from uuid import uuid4

import chromadb
import pytest

from app.services.vector_store import (
    ChromaStore,
    VectorStoreValidationError,
    build_vector_id,
)


def build_store() -> ChromaStore:
    client = chromadb.EphemeralClient()

    return ChromaStore(
        client=client,
        collection_name=f"test_{uuid4().hex}",
        expected_dimension=3,
    )


def build_metadata(
    *,
    doc_id: int,
    kb_id: int,
    version_no: int,
    chunk_index: int,
    content_version: int = 1,
):
    vector_id = build_vector_id(
        doc_id,
        version_no,
        chunk_index,
    )

    return {
        "docId": doc_id,
        "kbId": kb_id,
        "versionNo": version_no,
        "chunkIndex": chunk_index,
        "contentVersion": content_version,
        "vectorId": vector_id,
    }


def test_build_vector_id_uses_confirmed_rule():
    assert build_vector_id(101, 1, 3) == (
        "doc_101_v_1_idx_3"
    )


def test_upsert_and_search_with_doc_and_kb_filter():
    store = build_store()

    vector_id_1 = build_vector_id(101, 1, 1)
    vector_id_2 = build_vector_id(202, 1, 1)

    written = store.upsert_chunks(
        vector_ids=[
            vector_id_1,
            vector_id_2,
        ],
        embeddings=[
            [1.0, 0.0, 0.0],
            [0.0, 1.0, 0.0],
        ],
        documents=[
            "锅炉水压试验前应完成检查。",
            "汽轮机润滑油系统检查内容。",
        ],
        metadatas=[
            build_metadata(
                doc_id=101,
                kb_id=10,
                version_no=1,
                chunk_index=1,
            ),
            build_metadata(
                doc_id=202,
                kb_id=20,
                version_no=1,
                chunk_index=1,
            ),
        ],
    )

    assert written == 2
    assert store.count() == 2

    hits = store.search(
        query_embedding=[1.0, 0.0, 0.0],
        top_k=5,
        doc_ids=[101],
        kb_ids=[10],
    )

    assert len(hits) == 1
    assert hits[0].vector_id == vector_id_1
    assert hits[0].metadata["docId"] == 101
    assert hits[0].metadata["kbId"] == 10
    assert hits[0].similarity_score == pytest.approx(
        1.0
    )


def test_reembed_upsert_overwrites_original_vector():
    store = build_store()

    vector_id = build_vector_id(101, 1, 3)

    store.upsert_chunks(
        vector_ids=[vector_id],
        embeddings=[[1.0, 0.0, 0.0]],
        documents=["编辑前正文"],
        metadatas=[
            build_metadata(
                doc_id=101,
                kb_id=10,
                version_no=1,
                chunk_index=3,
                content_version=1,
            )
        ],
    )

    updated_metadata = build_metadata(
        doc_id=101,
        kb_id=10,
        version_no=1,
        chunk_index=3,
        content_version=2,
    )

    store.update_chunk(
        vector_id=vector_id,
        embedding=[0.0, 1.0, 0.0],
        document="编辑后的切片正文",
        metadata=updated_metadata,
    )

    assert store.count() == 1

    hits = store.search(
        query_embedding=[0.0, 1.0, 0.0],
        top_k=1,
        doc_ids=[101],
    )

    assert len(hits) == 1
    assert hits[0].vector_id == vector_id
    assert hits[0].document == "编辑后的切片正文"
    assert hits[0].metadata["contentVersion"] == 2


def test_delete_document_removes_all_versions():
    store = build_store()

    vector_ids = [
        build_vector_id(101, 1, 1),
        build_vector_id(101, 2, 1),
        build_vector_id(202, 1, 1),
    ]

    store.upsert_chunks(
        vector_ids=vector_ids,
        embeddings=[
            [1.0, 0.0, 0.0],
            [0.9, 0.1, 0.0],
            [0.0, 1.0, 0.0],
        ],
        documents=[
            "文档101版本1",
            "文档101版本2",
            "文档202版本1",
        ],
        metadatas=[
            build_metadata(
                doc_id=101,
                kb_id=10,
                version_no=1,
                chunk_index=1,
            ),
            build_metadata(
                doc_id=101,
                kb_id=10,
                version_no=2,
                chunk_index=1,
            ),
            build_metadata(
                doc_id=202,
                kb_id=20,
                version_no=1,
                chunk_index=1,
            ),
        ],
    )

    deleted_count = store.delete_document(101)

    assert deleted_count == 2
    assert store.count() == 1


def test_delete_document_version_only_removes_target_version():
    store = build_store()

    vector_id_v1 = build_vector_id(101, 1, 1)
    vector_id_v2 = build_vector_id(101, 2, 1)

    store.upsert_chunks(
        vector_ids=[
            vector_id_v1,
            vector_id_v2,
        ],
        embeddings=[
            [1.0, 0.0, 0.0],
            [0.0, 1.0, 0.0],
        ],
        documents=[
            "旧版本",
            "新版本",
        ],
        metadatas=[
            build_metadata(
                doc_id=101,
                kb_id=10,
                version_no=1,
                chunk_index=1,
            ),
            build_metadata(
                doc_id=101,
                kb_id=10,
                version_no=2,
                chunk_index=1,
            ),
        ],
    )

    deleted_count = store.delete_document_version(
        101,
        1,
    )

    assert deleted_count == 1
    assert store.count() == 1

    hits = store.search(
        query_embedding=[0.0, 1.0, 0.0],
        top_k=5,
        doc_ids=[101],
    )

    assert len(hits) == 1
    assert hits[0].vector_id == vector_id_v2


def test_wrong_dimension_is_rejected():
    store = build_store()

    vector_id = build_vector_id(101, 1, 1)

    with pytest.raises(
        VectorStoreValidationError,
        match="向量维度不正确",
    ):
        store.upsert_chunks(
            vector_ids=[vector_id],
            embeddings=[[1.0, 0.0]],
            documents=["测试正文"],
            metadatas=[
                build_metadata(
                    doc_id=101,
                    kb_id=10,
                    version_no=1,
                    chunk_index=1,
                )
            ],
        )


def test_metadata_vector_id_must_match_record_id():
    store = build_store()

    vector_id = build_vector_id(101, 1, 1)

    metadata = build_metadata(
        doc_id=101,
        kb_id=10,
        version_no=1,
        chunk_index=1,
    )
    metadata["vectorId"] = "wrong-vector-id"

    with pytest.raises(
        VectorStoreValidationError,
        match="recordId 一致",
    ):
        store.upsert_chunks(
            vector_ids=[vector_id],
            embeddings=[[1.0, 0.0, 0.0]],
            documents=["测试正文"],
            metadatas=[metadata],
        )