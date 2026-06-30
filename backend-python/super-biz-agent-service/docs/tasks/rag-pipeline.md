# rag-pipeline：RAG 检索、阈值、重排序与无资料策略

目标：实现可配置、可测试的 RAG Pipeline，支持多知识库过滤、阈值过滤、可选重排序和无资料策略。

## 最小任务

- [ ] 新增 `app/services/rag_pipeline_service.py`
- [ ] 定义 `RetrievedChunk` 数据结构
- [ ] 定义 `RagResult` 数据结构
- [ ] 定义 `RagConfig` 数据结构
- [ ] 从 `SettingsService` 读取 `rag.retrieval_mode`
- [ ] 从 `SettingsService` 读取 `rag.vector_top_k`
- [ ] 从 `SettingsService` 读取 `rag.vector_score_threshold`
- [ ] 从 `SettingsService` 读取 `rag.rerank_top_n`
- [ ] 从 `SettingsService` 读取 `rag.rerank_score_threshold`
- [ ] 从 `SettingsService` 读取 `rag.allow_fallback_answer`
- [ ] 实现知识库范围校验，过滤 disabled 知识库
- [ ] 修改 Milvus 写入 metadata，确保包含 `knowledge_base_id`、`document_id`、`chunk_id`
- [ ] 实现 Milvus metadata 过滤检索
- [ ] 如果 Milvus metadata 过滤不可用，实现应用层二次过滤降级
- [ ] 实现向量分数归一化，统一输出 `normalized_score`
- [ ] 实现相似度阈值过滤
- [ ] 新增 `app/services/rerank/base.py`
- [ ] 新增 `MockReranker` 用于测试
- [ ] 新增真实 Reranker 适配器占位实现
- [ ] 实现 `vector` 检索模式
- [ ] 实现 `vector_rerank` 检索模式
- [ ] 实现重排序失败降级策略
- [ ] 实现无资料默认提示策略
- [ ] 实现允许兜底回答时的普通 LLM 回答策略
- [ ] 兜底回答时设置 `is_knowledge_grounded=false`
- [ ] 在 SSE 中返回 `knowledge_retrieval`、`rerank`、`answer_generation` 节点状态
- [ ] 新增测试：指定知识库 ID 后只返回该知识库切片
- [ ] 新增测试：低于阈值的切片被过滤
- [ ] 新增测试：开启重排序后结果顺序变化
- [ ] 新增测试：重排序异常时按配置降级
- [ ] 新增测试：无资料时不生成伪引用

## 完成标准

- [ ] RAG 参数修改后下一次请求生效
- [ ] 多知识库联合检索可用
- [ ] 检索结果中保留知识库、文档和切片信息
- [ ] 无资料策略行为稳定且可测试

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增 `RagPipelineService`、`RetrievedChunk`、`RagResult`、Mock Reranker 与配置读取；支持知识库过滤、阈值过滤、重排序接口、无资料默认提示和兜底回答标记。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：真实 Milvus metadata 检索仍通过可注入 `vector_search` 适配，单测和主链路不依赖外部 Milvus。
