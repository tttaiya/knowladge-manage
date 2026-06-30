import { createRouter, createWebHistory } from 'vue-router'
import knowledgeRoutes from './modules/knowledge'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/knowledge-management/bases'
    },
    knowledgeRoutes
  ]
})

export default router
