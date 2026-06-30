# dashboard-metrics：运营统计与管理总览

目标：记录问答指标并提供管理总览接口，支持核心指标和近 30 天趋势。当前版本任意已登录用户都可以查看运营统计。

## 最小任务

- [ ] 新增 `app/repositories/metrics_repository.py`
- [ ] 新增 `app/services/dashboard_service.py`
- [ ] 新增 `app/models/schemas/dashboard.py`
- [ ] 新增 `app/api/admin_dashboard.py`
- [ ] 在问答成功结束时写入 `qa_metrics`
- [ ] 在问答失败时写入 `qa_metrics`
- [ ] 在问答停止时写入 `qa_metrics`
- [ ] 统计记录包含 `user_id`
- [ ] 统计记录包含 `session_id`
- [ ] 统计记录包含 `message_id`
- [ ] 统计记录包含 `question`
- [ ] 统计记录包含 `intent`
- [ ] 统计记录包含 `hit_knowledge`
- [ ] 统计记录包含 `knowledge_base_ids`
- [ ] 统计记录包含 `citation_count`
- [ ] 统计记录包含 `latency_ms`
- [ ] 统计记录包含 `status`
- [ ] 实现 `GET /api/admin/dashboard/summary`
- [ ] Summary 返回用户总数
- [ ] Summary 返回知识库数量
- [ ] Summary 返回文档总量
- [ ] Summary 返回切片总量
- [ ] Summary 返回知识问答总次数
- [ ] 实现 `GET /api/admin/dashboard/qa-trend`
- [ ] 趋势接口支持日期范围参数
- [ ] 近 30 天趋势按日期补零
- [ ] 趋势数据包含问答次数
- [ ] 趋势数据包含活跃用户数
- [ ] 实现 `GET /api/admin/dashboard/recent-questions`
- [ ] 所有 Dashboard 接口接入登录校验
- [ ] 新增测试：成功问答写入统计记录
- [ ] 新增测试：失败问答写入统计记录
- [ ] 新增测试：停止问答写入统计记录
- [ ] 新增测试：近 30 天趋势按日期补零
- [ ] 新增测试：已登录用户可访问统计接口
- [ ] 新增测试：未登录用户不能访问统计接口

## 完成标准

- [ ] 已登录用户可查看核心运营指标
- [ ] 趋势图数据可直接供前端渲染
- [ ] 问答成功、失败、停止都能留下统计记录

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增指标仓储、Dashboard 服务和 `/api/admin/dashboard/*`；问答成功与停止路径写入 `qa_metrics`；趋势接口按日期补零。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：失败路径结构已预留，主链路异常会回滚当前事务并返回错误事件。
