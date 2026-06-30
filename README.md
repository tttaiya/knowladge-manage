# 知识管理模块 - 杨家杭交付包

本目录按 `杨家杭实施步骤_v16.md` 生成，用于完成异步任务、Worker、RabbitMQ、Nacos、Gateway、Nginx、Docker Compose、启动/停止脚本和 SQL 交付。

## 目录

- `backend-java/knowledge-management/km-admin-service/`：任务创建、查询、失败重试、Worker claim/heartbeat、结果消费、策略重处理和 PURGE 调度。
- `backend-java/knowledge-management/km-worker-service/`：四类异步任务消费者、全局解析并发、心跳续约、延迟重试、FastAPI 调用和结果上报。
- `backend-java/knowledge-management/km-search-service/`：Compose 联调占位检索服务。
- `backend-java/gateway-service/`：统一网关路由。
- `backend-python/km-ai-service/`：课程联调用 FastAPI mock，占位实现解析、切片、向量化、向量删除契约。
- `infra/`：RabbitMQ、Nacos、Nginx、Docker Compose。
- `sql/`：任务、状态日志、版本、幂等事件、物理清理审计等表结构。
- `scripts/`：本地全量启动和停止脚本。
- `docs/deploy/杨家杭交付与部署说明.md`：最终交付清单和详细部署步骤。

验收启动方式：

```powershell
.\scripts\start-local.ps1
```

