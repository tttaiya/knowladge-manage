import { h } from 'vue'
import { RouterView, type RouteRecordRaw } from 'vue-router'

const knowledgeRoutes: RouteRecordRaw = {
  path: '/knowledge-management',
  redirect: '/knowledge-management/bases',
  component: {
    render() {
      return h(RouterView)
    }
  },
  children: [
    {
      path: 'bases',
      name: 'KnowledgeBaseList',
      component: () => import('../../views/knowledge/KnowledgeBaseList.vue')
    },
    {
      path: 'bases/:id',
      name: 'KnowledgeBaseDetail',
      component: () => import('../../views/knowledge/KnowledgeBaseDetail.vue')
    }
  ]
}

export default knowledgeRoutes
