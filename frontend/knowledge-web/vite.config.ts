import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

/**
 * 知识管理 Vue 子应用（R18）。
 *
 * base 必须设为 /knowledge/ —— 这是 Nginx 反向代理入口的根路径：
 *   location /knowledge/ { proxy_pass http://knowledge-web/; }
 *
 * 配合 createWebHistory(import.meta.env.BASE_URL) 使用，确保 SPA 内部
 * 跳转（如 /knowledge/bases/1/documents）能正确匹配路由。
 */
export default defineConfig({
  base: '/knowledge/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
  },
})
