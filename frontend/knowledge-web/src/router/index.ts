import { createRouter, createWebHistory, RouteRecordRaw } from 'vue-router'

/**
 * 知识管理子应用路由（R18）。
 *
 * <p>所有路由以 /knowledge/ 为根，createWebHistory 用 import.meta.env.BASE_URL
 * 自动适配。直接访问 /knowledge/bases/1/documents 也能正确匹配子应用路由。
 *
 * <p>路由守卫：无 access_token 跳回根路径（由 super-biz-agent 处理登录）。
 * 后端 Gateway 仍是最终鉴权入口，前端守卫只负责用户体验。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/bases',
  },
  {
    path: '/bases',
    name: 'KnowledgeBaseList',
    component: () => import('@/views/knowledge/KnowledgeBaseList.vue'),
  },
  {
    // F2 commit #30：知识库详情页（与 /bases/:kbId/documents 兄弟）
    path: '/bases/:kbId',
    name: 'KnowledgeBaseDetail',
    component: () => import('@/views/knowledge/KnowledgeBaseDetail.vue'),
    props: true,
  },
  {
    path: '/bases/:kbId/documents',
    name: 'DocumentList',
    component: () => import('@/views/knowledge/DocumentList.vue'),
    props: true,
  },
  {
    path: '/bases/:kbId/recycle-bin',
    name: 'RecycleBin',
    component: () => import('@/views/knowledge/RecycleBin.vue'),
    props: true,
  },
  {
    path: '/review',
    name: 'ReviewWorkbench',
    component: () => import('@/views/knowledge/ReviewWorkbench.vue'),
  },
  {
    path: '/search',
    name: 'RetrievalPage',
    component: () => import('@/views/knowledge/RetrievalPage.vue'),
  },
  {
    path: '/config',
    name: 'ConfigPage',
    component: () => import('@/views/knowledge/ConfigPage.vue'),
  },
  {
    path: '/statistics',
    name: 'StatisticsPage',
    component: () => import('@/views/knowledge/StatisticsPage.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/knowledge/NotFound.vue'),
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

router.beforeEach((to, from, next) => {
  if (typeof window !== 'undefined') {
    const token = window.localStorage.getItem('access_token')
    if (!token) {
      window.location.href = '/'
      return
    }
  }
  next()
})

export default router
