# auth-permission：认证与 Token 权限

目标：实现用户注册、登录、JWT Access Token、Refresh Token、退出登录和登录态权限控制。当前版本采用单一登录用户权限模型，所有已登录用户都可以使用管理后台功能。

## 最小任务

- [ ] 新增 `app/auth/password.py`，实现密码哈希和密码校验
- [ ] 新增 `app/auth/tokens.py`，实现 Access Token 签发与解析
- [ ] 新增 Refresh Token 随机生成和哈希存储方法
- [ ] 新增 `app/auth/dependencies.py`，实现 `get_current_user`
- [ ] 新增 `app/auth/permissions.py`，实现 `require_login` 或等价的登录态权限依赖
- [ ] 新增 `app/repositories/user_repository.py`
- [ ] 新增 `app/services/auth_service.py`
- [ ] 新增 `app/models/schemas/auth.py`，定义注册、登录、刷新、当前用户响应模型
- [ ] 新增 `app/api/auth.py`
- [ ] 实现 `POST /api/auth/register`
- [ ] 实现 `POST /api/auth/login`
- [ ] 实现 `POST /api/auth/refresh`
- [ ] 实现 `POST /api/auth/logout`
- [ ] 实现 `GET /api/auth/me`
- [ ] 将 `/api/chat`、`/api/chat_stream`、`/api/upload`、`/api/index_directory`、`/api/badcases` 接入登录校验
- [ ] 将 `/api/admin/*` 接入登录校验，任意已登录用户均可访问
- [ ] 新增测试：密码哈希不等于明文
- [ ] 新增测试：正确密码登录成功
- [ ] 新增测试：错误密码登录失败
- [ ] 新增测试：无 Token 访问受保护接口返回 401
- [ ] 新增测试：已登录用户可访问管理接口
- [ ] 新增测试：未登录用户访问管理接口返回 401
- [ ] 新增测试：Refresh Token 刷新后旧 Token 失效

## 完成标准

- [ ] 所有业务接口默认需要登录
- [ ] 后端采用单一登录用户权限模型，任意已登录用户具备管理功能权限
- [ ] Access Token 过期后可用 Refresh Token 刷新
- [ ] 退出登录后 Refresh Token 不可继续使用

## 完成记录

- 完成时间：2026-06-28
- 主要改动：新增 `app/auth/*`、用户仓储、认证服务和 `/api/auth/*` 接口；业务路由和后台路由接入 `require_login`；Refresh Token 落库并支持刷新轮换与退出吊销。
- 测试命令：`.\.venv\Scripts\python.exe -m pytest`
- 测试结果：`17 passed`
- 实现备注：密码哈希使用标准库 PBKDF2-HMAC-SHA256，避免引入额外运行时依赖。
