from fastapi import FastAPI

app = FastAPI(title="KM AI Service Mock")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/internal/ai/parse")
def parse(payload: dict):
    task = payload if "taskId" in payload else payload.get("task", {})
    return {
        "docId": task.get("docId"),
        "blocks": [
            {"type": "paragraph", "text": "课程设计联调解析文本", "pageNo": 1}
        ],
    }


@app.post("/internal/ai/chunk")
def chunk(payload: dict):
    task = payload.get("task", {})
    doc_id = task.get("docId")
    target_version = task.get("targetVersionNo") or 1
    return {
        "docId": doc_id,
        "chunks": [
            {
                "docId": doc_id,
                "versionNo": target_version,
                "chunkIndex": 1,
                "content": "课程设计联调解析文本",
                "chapterPath": "demo",
                "pageNo": 1,
                "chunkType": "paragraph",
                "charCount": 10,
            }
        ],
    }


@app.post("/internal/ai/embed")
def embed(payload: dict):
    task = payload.get("task", payload)
    doc_id = task.get("docId")
    target_version = task.get("targetVersionNo") or 1
    vector_id = f"vec-{doc_id}-{target_version}-1"
    return {
        "docId": doc_id,
        "vectorIds": [vector_id],
        "chunks": [
            {
                "docId": doc_id,
                "versionNo": target_version,
                "chunkIndex": 1,
                "content": "课程设计联调解析文本",
                "vectorId": vector_id,
            }
        ],
    }


@app.delete("/internal/ai/vectors/{doc_id}")
def delete_vectors(doc_id: int):
    return {"docId": doc_id, "deleted": True}


@app.delete("/internal/ai/vectors/{doc_id}/versions/{version_no}")
def delete_vectors_version(doc_id: int, version_no: int):
    return {"docId": doc_id, "versionNo": version_no, "deleted": True}

