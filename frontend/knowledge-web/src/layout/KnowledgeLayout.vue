<template>
  <el-container class="knowledge-layout">
    <el-header class="km-header">
      <div class="km-title">知识管理</div>
      <div class="km-user">
        <span v-if="username">{{ username }}</span>
        <el-button type="text" size="small" @click="logout">退出</el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside class="km-aside" width="200px">
        <el-menu
          :default-active="activeMenu"
          :router="true"
          class="km-menu"
        >
          <el-menu-item index="/bases">知识库</el-menu-item>
          <el-menu-item index="/review">审核工作台</el-menu-item>
          <el-menu-item index="/search">知识检索</el-menu-item>
          <el-menu-item index="/config">系统配置</el-menu-item>
          <el-menu-item index="/statistics">数据统计</el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="km-main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => {
  // 选中最近的有菜单项的祖先路径
  const path = route.path
  if (path.startsWith('/bases')) return '/bases'
  if (path.startsWith('/review')) return '/review'
  if (path.startsWith('/search')) return '/search'
  if (path.startsWith('/config')) return '/config'
  if (path.startsWith('/statistics')) return '/statistics'
  return path
})

const username = computed(() => {
  if (typeof window === 'undefined') return ''
  return window.localStorage.getItem('username') || ''
})

function logout() {
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem('access_token')
    window.localStorage.removeItem('username')
    window.location.href = '/'
  }
}

// 让 router 不报未用警告
void router
</script>

<style scoped>
.knowledge-layout {
  height: 100vh;
}
.km-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #1f6feb;
  color: #fff;
  padding: 0 24px;
}
.km-title {
  font-size: 18px;
  font-weight: 600;
}
.km-user {
  display: flex;
  gap: 12px;
  align-items: center;
}
.km-user .el-button {
  color: #fff;
}
.km-aside {
  background: #fafafa;
  border-right: 1px solid #ebeef5;
}
.km-menu {
  border-right: none;
}
.km-main {
  padding: 16px;
  background: #fff;
}
</style>
