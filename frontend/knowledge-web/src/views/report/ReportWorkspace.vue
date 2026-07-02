<template>
  <section class="report-workspace">
    <PowerBackground />

    <div class="app-shell">
      <SidebarNav :active-key="activeView" @select="setActiveView" />

      <div class="app-main">
        <HeaderBar
          :title="viewTitle"
          subtitle=""
          :online="backend.online"
          :backend-label="backend.text"
          :user-label="username"
          :loading="backend.loading"
          @refresh-backend="checkBackend"
          @logout="goHome"
        />

        <main class="app-content">
          <transition name="fade" mode="out-in">
            <DashboardView v-if="activeView === 'dashboard'" key="dashboard" />
            <ReportCreateView v-else-if="activeView === 'create'" key="create" @created="handleReportCreated" />
            <OutlineManager
              v-else-if="activeView === 'outline'"
              key="outline"
              v-model:report-id="currentReportId"
              @go-generation="setActiveView('generation')"
            />
            <GenerationView
              v-else-if="activeView === 'generation'"
              key="generation"
              v-model:report-id="currentReportId"
              @go-editor="setActiveView('editor')"
            />
            <ChapterEditorView
              v-else-if="activeView === 'editor'"
              key="editor"
              v-model:report-id="currentReportId"
            />
            <RecordsView v-else-if="activeView === 'records'" key="records" @select-report="handleSelectReport" />
            <TemplatesView v-else-if="activeView === 'templates'" key="templates" />
          </transition>
        </main>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { reportApi } from '@/api/modules/report'
import '@/styles/report.css'
import DashboardView from '@/components/report/DashboardView.vue'
import ReportCreateView from '@/components/report/ReportCreateView.vue'
import OutlineManager from '@/components/report/OutlineManager.vue'
import GenerationView from '@/components/report/GenerationView.vue'
import ChapterEditorView from '@/components/report/ChapterEditorView.vue'
import RecordsView from '@/components/report/RecordsView.vue'
import TemplatesView from '@/components/report/TemplatesView.vue'
import PowerBackground from '@/components/report/common/PowerBackground.vue'
import SidebarNav from '@/components/report/layout/SidebarNav.vue'
import HeaderBar from '@/components/report/layout/HeaderBar.vue'

type ViewKey = 'dashboard' | 'create' | 'outline' | 'generation' | 'editor' | 'records' | 'templates'

const router = useRouter()
const activeView = ref<ViewKey>('dashboard')
const currentReportId = ref(Number(window.localStorage.getItem('current_report_id') || 1))
const backend = reactive({ online: false, loading: false, text: '后端未检查' })

const titles: Record<ViewKey, string> = {
  dashboard: '工作台',
  create: '报告创建',
  outline: '大纲管理',
  generation: '正文生成',
  editor: '报告编辑',
  records: '报告记录',
  templates: '模板管理',
}

const viewTitle = computed(() => titles[activeView.value] || '报告生成模块')
const username = computed(() => window.localStorage.getItem('username') || '统一登录用户')

onMounted(() => {
  checkBackend()
})

function setActiveView(viewKey: ViewKey) {
  if (viewKey !== 'records') {
    window.dispatchEvent(new CustomEvent('report:hide-record-overlays'))
  }
  activeView.value = viewKey
}

function goHome() {
  router.push('/bases')
}

function handleReportCreated(reportId: number | string) {
  currentReportId.value = Number(reportId)
  window.localStorage.setItem('current_report_id', String(reportId))
  activeView.value = 'outline'
}

function handleSelectReport(reportId: number | string) {
  currentReportId.value = Number(reportId)
  window.localStorage.setItem('current_report_id', String(reportId))
  activeView.value = 'editor'
}

async function checkBackend() {
  backend.loading = true
  try {
    await reportApi.health()
    backend.online = true
    backend.text = '后端已连接'
  } catch (error) {
    backend.online = false
    backend.text = '后端未连接'
    const message = error instanceof Error ? error.message : String(error)
    ElMessage.warning(`报告服务检测失败：${message}`)
  } finally {
    backend.loading = false
  }
}
</script>

<style scoped>
.app-shell {
  position: relative;
  z-index: 1;
  min-height: calc(100vh - 32px);
  display: flex;
}

.app-main {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.app-content {
  min-width: 0;
  flex: 1;
  padding: 24px;
  overflow: auto;
  position: relative;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.fade-enter-to,
.fade-leave-from {
  opacity: 1;
  transform: translateY(0);
}
</style>
