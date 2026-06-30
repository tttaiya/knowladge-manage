# system-settings：动态系统配置

目标：实现后台可编辑、运行时生效的系统配置，覆盖 RAG、LLM、安全和上下文参数。当前版本任意已登录用户都可以读取和修改配置。

## 最小任务

- [ ] 新增 `app/repositories/settings_repository.py`
- [ ] 新增 `app/services/settings_service.py`
- [ ] 新增 `app/models/schemas/settings.py`
- [ ] 新增 `app/api/admin_settings.py`
- [ ] 实现系统启动时初始化默认配置项
- [ ] 实现配置读取优先级：数据库优先，其次 `.env`，最后代码默认值
- [ ] 实现配置内存缓存
- [ ] 实现配置更新后刷新缓存
- [ ] 实现 `GET /api/admin/settings`
- [ ] 实现 `PATCH /api/admin/settings/{key}`
- [ ] 实现 `POST /api/admin/settings/reset`
- [ ] 为配置项实现类型校验：string、int、float、bool、json、secret
- [ ] 为 RAG Top K、阈值等数值配置实现范围校验
- [ ] 敏感配置返回时脱敏
- [ ] 敏感配置写入时支持加密或至少避免日志输出明文
- [ ] 将 RAG Pipeline 改为从 `SettingsService` 获取配置
- [ ] 将 ContextService 改为从 `SettingsService` 获取配置
- [ ] 将 Token 过期时间改为从 `SettingsService` 获取配置
- [ ] 新增测试：已登录用户可修改配置
- [ ] 新增测试：未登录用户不能修改配置
- [ ] 新增测试：敏感配置返回脱敏值
- [ ] 新增测试：修改 Top K 后下一次 RAG 检索生效
- [ ] 新增测试：重置默认值后配置恢复

## 完成标准

- [ ] 常用参数修改无需重启服务
- [ ] 后台接口可读取配置说明、默认值和当前值
- [ ] API Key 等敏感配置不会明文返回给前端

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增系统配置仓储、服务、Schema 和 `/api/admin/settings` 接口；初始化 RAG、LLM、安全和上下文默认配置；敏感配置脱敏展示；配置修改后后续请求读取数据库值。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：敏感配置当前避免明文返回，未引入额外加密组件。
