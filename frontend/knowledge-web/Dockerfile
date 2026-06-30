# ============================================================================
# 知识管理 Vue 子应用 - 多阶段构建 (R18)
# ============================================================================
# 阶段 1：Node 16 编译前端
# 阶段 2：Nginx 1.20 托管 dist
# ============================================================================
FROM node:16-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

# ----------------------------------------------------------------------------
FROM nginx:1.20.1-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD wget -qO- http://127.0.0.1/healthz || exit 1
