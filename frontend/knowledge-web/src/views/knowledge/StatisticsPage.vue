<template>
  <main class="stats-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">知识管理</p>
        <h1>数据统计</h1>
        <p class="subtitle">实时反映知识库、文档与处理任务状态</p>
      </div>
      <el-button :loading="loading" type="primary" plain @click="reload">手动刷新</el-button>
    </header>

    <!-- 错误态 -->
    <el-card v-if="errorMessage" class="state-card error-card" shadow="never">
      <el-empty :description="errorMessage">
        <el-button type="primary" @click="reload">重试</el-button>
      </el-empty>
    </el-card>

    <!-- 加载 / 正常态骨架与内容 -->
    <template v-else>
      <section class="metric-grid" aria-label="核心指标">
        <el-card v-for="card in coreCards" :key="card.key" class="metric-card core" shadow="never" v-loading="loading">
          <template #header>
            <div class="metric-header">
              <span class="metric-title">{{ card.title }}</span>
              <el-tag :type="card.tagType" effect="plain" size="small">{{ card.tag }}</el-tag>
            </div>
          </template>
          <div class="metric-value">{{ formatNumber(card.value) }}</div>
          <p class="metric-desc">{{ card.desc }}</p>
        </el-card>
      </section>

      <section class="metric-grid status-grid" aria-label="状态指标">
        <el-card v-for="card in statusCards" :key="card.key" class="metric-card status" shadow="never" v-loading="loading">
          <template #header>
            <div class="metric-header">
              <span class="metric-title">{{ card.title }}</span>
              <el-tag :type="card.tagType" effect="plain" size="small">{{ card.tag }}</el-tag>
            </div>
          </template>
          <div class="metric-value">{{ formatNumber(card.value) }}</div>
          <p class="metric-desc">{{ card.desc }}</p>
        </el-card>
      </section>

      <el-card class="trend-card" shadow="never">
        <template #header>
          <div class="trend-header">
            <div>
              <span class="trend-title">近 {{ overview?.documentTrend?.length || 30 }} 天文档上传趋势</span>
              <p class="trend-sub">按 created_at 分日统计（服务端补零）</p>
            </div>
            <span class="trend-meta">数据更新于 {{ lastUpdatedText }}</span>
          </div>
        </template>

        <div v-loading="loading" class="chart-wrapper">
          <div ref="chartEl" class="chart-canvas"></div>
          <div v-if="!hasTrendData && !loading" class="chart-empty">暂无趋势数据</div>
        </div>
      </el-card>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElButton, ElCard, ElEmpty, ElTag } from 'element-plus'
import { fetchStatsOverview, type StatsOverview } from '@/api/modules/stats'

// 仅注册实际用到的 ECharts 模块，减小打包体积（按需引入）
echarts.use([
  LineChart,
  GridComponent,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  CanvasRenderer,
])

type ElTagType = 'success' | 'warning' | 'info' | 'danger'

interface CoreCard {
  key: string
  title: string
  tag: string
  tagType: ElTagType
  value: number
  desc: string
}

interface StatusCard {
  key: string
  title: string
  tag: string
  tagType: ElTagType
  value: number
  desc: string
}

const overview = ref<StatsOverview | null>(null)
const loading = ref(false)
const errorMessage = ref('')
const lastUpdatedAt = ref<Date | null>(null)

const chartEl = ref<HTMLDivElement | null>(null)
let chartInstance: echarts.ECharts | null = null
let pollTimer: ReturnType<typeof setInterval> | null = null
let resizeHandler: (() => void) | null = null

const hasTrendData = computed(() => (overview.value?.documentTrend?.length ?? 0) > 0)

const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) return '尚未加载'
  const d = lastUpdatedAt.value
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

/** 安全取数：overview 为 null 时返回 0，避免模板崩 */
function safeNumber(field: keyof Pick<StatsOverview, 'knowledgeBaseTotal' | 'documentTotal' | 'chunkTotal' | 'documentReady' | 'documentPendingReview' | 'documentFailed' | 'taskProcessing'>): number {
  return overview.value?.[field] ?? 0
}

const coreCards = computed<CoreCard[]>(() => [
  {
    key: 'kb',
    title: '知识库总数',
    tag: 'F2',
    tagType: 'info',
    value: safeNumber('knowledgeBaseTotal'),
    desc: '当前未删除（is_deleted=0）的知识库数量',
  },
  {
    key: 'doc',
    title: '有效文档总数',
    tag: 'F3',
    tagType: 'success',
    value: safeNumber('documentTotal'),
    desc: '未删除的文档总数（包含所有状态）',
  },
  {
    key: 'chunk',
    title: '有效切片总数',
    tag: 'F3.9',
    tagType: 'info',
    value: safeNumber('chunkTotal'),
    desc: 'is_active=1 且所属文档未删除的切片数',
  },
])

const statusCards = computed<StatusCard[]>(() => [
  {
    key: 'ready',
    title: 'READY 文档',
    tag: '可检索',
    tagType: 'success',
    value: safeNumber('documentReady'),
    desc: 'document_status = READY 的文档数',
  },
  {
    key: 'pending',
    title: 'PENDING_REVIEW 文档',
    tag: '待审核',
    tagType: 'warning',
    value: safeNumber('documentPendingReview'),
    desc: '等待审核工作台处理的文档数',
  },
  {
    key: 'failed',
    title: 'FAILED 文档',
    tag: '处理失败',
    tagType: 'danger',
    value: safeNumber('documentFailed'),
    desc: '处理失败的文档数（含重试耗尽）',
  },
  {
    key: 'processing',
    title: '处理中任务',
    tag: 'QUEUED + RUNNING',
    tagType: 'info',
    value: safeNumber('taskProcessing'),
    desc: 'Worker 当前正在排队的任务数',
  },
])

function formatNumber(n: number): string {
  if (!Number.isFinite(n)) return '0'
  return n.toLocaleString('zh-CN')
}

/** 用 NaN-safe 兜底构造 ECharts series 数据 */
function buildChartOption(data: StatsOverview) {
  const trend = data.documentTrend ?? []
  const dates = trend.map((d) => d.date)
  const counts = trend.map((d) => d.count ?? 0)

  return {
    grid: { left: 48, right: 24, top: 36, bottom: 64 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      formatter: (params: any) => {
        const arr = Array.isArray(params) ? params : [params]
        const lines = arr
          .map((p) => `${p.marker}${p.seriesName}: <b>${p.value}</b>`)
          .join('<br/>')
        return `${arr[0]?.axisValueLabel ?? ''}<br/>${lines}`
      },
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLabel: {
        rotate: dates.length > 14 ? 40 : 0,
        formatter: (value: string) => (typeof value === 'string' && value.length >= 5 ? value.slice(5) : value),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {
        formatter: (v: number) => (Number.isInteger(v) ? String(v) : ''),
      },
    },
    series: [
      {
        name: '上传文档数',
        type: 'line',
        data: counts,
        smooth: false,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { width: 2 },
        areaStyle: { opacity: 0.15 },
        itemStyle: { color: '#409EFF' },
      },
    ],
  }
}

function ensureChartInstance(): echarts.ECharts | null {
  if (!chartEl.value) return null
  if (chartInstance) return chartInstance
  chartInstance = echarts.init(chartEl.value)
  return chartInstance
}

function renderChart() {
  const inst = ensureChartInstance()
  if (!inst || !overview.value) return
  inst.setOption(buildChartOption(overview.value), { notMerge: true })
}

function disposeChart() {
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
}

async function reload() {
  loading.value = true
  errorMessage.value = ''
  try {
    const data = await fetchStatsOverview(30)
    overview.value = data
    lastUpdatedAt.value = new Date()
    await nextTick()
    renderChart()
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || '加载统计概览失败'
    errorMessage.value = `加载失败：${msg}`
    overview.value = null
    disposeChart()
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(reload, 60_000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  // 监听窗口尺寸变化，确保图表自适应
  resizeHandler = () => chartInstance?.resize()
  window.addEventListener('resize', resizeHandler)

  await reload()
  startPolling()
})

// 数据变化时重绘（例如轮询拿到新数据）
watch(overview, () => {
  if (overview.value) {
    nextTick(renderChart)
  }
})

onBeforeUnmount(() => {
  stopPolling()
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
    resizeHandler = null
  }
  disposeChart()
})
</script>

<style scoped>
.stats-page {
  min-height: 100vh;
  padding: 32px;
  background: #eef2f7;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  max-width: 1280px;
  margin: 0 auto 24px;
}

.page-header .eyebrow {
  margin: 0 0 4px;
  color: #64748b;
  font-size: 14px;
}

.page-header h1 {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 650;
}

.page-header .subtitle {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.state-card {
  max-width: 720px;
  margin: 24px auto;
}

.error-card :deep(.el-card__body) {
  padding: 24px 0;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  max-width: 1280px;
  margin: 0 auto 16px;
}

.metric-grid.status-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-card {
  border-radius: 8px;
  border-color: #dbe3ee;
}

.metric-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.metric-title {
  font-weight: 650;
  color: #1f2937;
}

.metric-value {
  font-size: 32px;
  font-weight: 700;
  color: #111827;
  letter-spacing: 0;
  line-height: 1.2;
}

.metric-desc {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 13px;
  min-height: 18px;
}

.trend-card {
  max-width: 1280px;
  margin: 16px auto 0;
  border-radius: 8px;
  border-color: #dbe3ee;
}

.trend-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.trend-title {
  font-weight: 650;
  color: #1f2937;
}

.trend-sub {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.trend-meta {
  color: #94a3b8;
  font-size: 12px;
  white-space: nowrap;
}

.chart-wrapper {
  position: relative;
  width: 100%;
  height: 360px;
}

.chart-canvas {
  width: 100%;
  height: 100%;
}

.chart-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 14px;
  pointer-events: none;
}

@media (max-width: 1080px) {
  .metric-grid,
  .metric-grid.status-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-page {
    padding: 20px;
  }
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }
  .metric-grid,
  .metric-grid.status-grid {
    grid-template-columns: 1fr;
  }
  .chart-wrapper {
    height: 280px;
  }
}
</style>