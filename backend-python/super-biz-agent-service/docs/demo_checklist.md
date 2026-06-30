# Day09 演示检查清单

## 启动前检查

- `.env` 已配置 `DASHSCOPE_API_KEY`。
- Docker Desktop 已启动。
- Milvus 已通过 `docker compose -f vector-database.yml up -d` 启动。
- FastAPI 已启动在 `http://localhost:9900`。
- `knowledge-docs/` 文档已上传到向量库。

## 功能检查

- `/api/health` 返回正常。
- `/api/chat` 可以回答知识库问题。
- `traces/` 中产生 trace 文件。
- `/api/badcases` 可以创建和查询复盘记录。

## 风险预案

- 如果 Milvus 未启动，先演示 trace 和 bad case。
- 如果 LLM 网络不可用，演示单元测试和本地评测集。

## 面试题

1. Demo 时外部依赖不可用怎么办？
2. 如何证明你的功能不是只在一个样例上有效？
3. 为什么要准备演示检查清单？
4. 如果现场诊断结果不理想，你怎么解释？
5. 你会如何把这个项目部署到生产？
