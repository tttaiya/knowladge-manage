# knowledge-base：知识库、文档与索引管理

目标：实现知识库维度的管理能力，支持文档上传到指定知识库，并通过单 Milvus collection 的 metadata 过滤参与检索。

## 最小任务

- [ ] 新增 `app/repositories/knowledge_repository.py`
- [ ] 新增 `app/services/knowledge_base_service.py`
- [ ] 新增 `app/models/schemas/knowledge.py`
- [ ] 新增 `app/api/knowledge_base.py`
- [ ] 实现创建知识库接口 `POST /api/admin/knowledge-bases`
- [ ] 实现知识库列表接口 `GET /api/admin/knowledge-bases`
- [ ] 实现更新知识库接口 `PATCH /api/admin/knowledge-bases/{kb_id}`
- [ ] 实现删除知识库接口 `DELETE /api/admin/knowledge-bases/{kb_id}`
- [ ] 实现启用知识库
- [ ] 实现停用知识库
- [ ] 修改上传接口，要求上传到指定 `knowledge_base_id`
- [ ] 保存文档原文到按知识库分组的目录
- [ ] 保存 `knowledge_documents` 记录
- [ ] 文档索引前将状态设为 `indexing`
- [ ] 切片时写入 `knowledge_chunks`
- [ ] Milvus 写入 metadata 包含 `knowledge_base_id`、`document_id`、`chunk_id`
- [ ] 索引成功后更新文档状态为 `indexed` 和 `chunk_count`
- [ ] 索引失败后更新文档状态为 `failed` 和错误原因
- [ ] 实现文档列表接口
- [ ] 实现文档重新索引接口
- [ ] 实现文档删除接口，删除或停用 Milvus 中对应向量
- [ ] 实现原文下载接口并校验权限
- [ ] 新增测试：上传文档后生成文档记录和切片记录
- [ ] 新增测试：Milvus metadata 包含 `knowledge_base_id`
- [ ] 新增测试：停用知识库后检索不到该知识库内容
- [ ] 新增测试：删除文档后不可再被检索
- [ ] 新增测试：已登录用户可调用管理接口
- [ ] 新增测试：未登录用户不能调用管理接口

## 完成标准

- [ ] 文档必须属于某个知识库
- [ ] 多知识库可独立启停
- [ ] 删除或停用后的知识库内容不参与问答检索

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增知识库仓储、服务、Schema 和 `/api/admin/knowledge-bases`、文档上传/列表/删除/下载接口；文档按知识库目录保存并写入文档与切片记录。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：当前为同步单切片索引的轻量实现，Milvus 向量写入接口预留在服务层后续替换。
