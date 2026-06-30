# knowledge-web

R18：知识管理 Vue 子应用，部署在 `http://<nginx>/knowledge/` 路径下。

## 路由前缀

所有页面都以 `/knowledge/` 为根，浏览器访问路径如：

- `http://localhost:8080/knowledge/bases` — 知识库列表
- `http://localhost:8080/knowledge/bases/1/documents` — 知识库 1 的文档列表
- `http://localhost:8080/knowledge/bases/1/recycle-bin` — 回收站
- `http://localhost:8080/knowledge/review` — 审核工作台
- `http://localhost:8080/knowledge/search` — 知识检索
- `http://localhost:8080/knowledge/config` — 系统配置
- `http://localhost:8080/knowledge/statistics` — 数据统计

## 部署

```bash
docker build -t knowledge-web .
```

或通过主项目 Docker Compose：

```bash
docker compose -f infra/docker/docker-compose.demo.yml up -d --build
```

## 鉴权约定

- 路由守卫：无 `access_token` 跳回根路径（super-biz-agent 登录页）
- 后端 Gateway `UserContextGatewayFilter` 仍是最终鉴权入口
- 前端守卫只负责 UX，401 由 Gateway 处理
