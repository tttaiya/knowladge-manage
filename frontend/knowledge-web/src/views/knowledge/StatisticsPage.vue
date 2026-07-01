<template>
  <div class="dashboard-container">
    <!-- 顶部：数据总览 -->
    <div class="section-header">
      <div class="title-group">
        <el-icon class="header-icon"><Grid /></el-icon>
        <span class="main-title">数据总览</span>
      </div>
      <div class="subtitle">系统运行状态与核心指标一览</div>
    </div>

    <el-row :gutter="16" class="stat-cards-row">
      <el-col :xs="12" :sm="8" :md="4" :lg="4" v-for="(item, index) in statsData" :key="index">
        <div class="stat-card">
          <div class="stat-content">
            <div class="stat-number" :style="{ color: item.color }">{{ item.value }}</div>
            <div class="stat-label">
              <span>{{ item.label }}</span>
              <el-icon class="stat-icon"><component :is="item.icon" /></el-icon>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 底部：时间分布 -->
    <div class="section-header mt-4">
      <div class="title-group">
        <el-icon class="header-icon"><TrendCharts /></el-icon>
        <span class="main-title">时间分布</span>
      </div>
      <div class="subtitle">近30天活动趋势（按日统计）</div>
    </div>

    <el-row :gutter="16" class="charts-row">
      <!-- 图表1：文档上传趋势 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card highlight-border">
          <div class="chart-header">
            <el-icon class="chart-icon blue"><DocumentCopy /></el-icon>
            <span class="chart-title">文档上传 · 近30天</span>
          </div>
          <div class="chart-container" ref="chartUploadRef"></div>
        </div>
      </el-col>

      <!-- 图表2：任务处理趋势 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-header">
            <el-icon class="chart-icon yellow"><ChatDotSquare /></el-icon>
            <span class="chart-title">任务处理 · 近30天</span>
          </div>
          <div class="chart-container" ref="chartTaskRef"></div>
        </div>
      </el-col>

      <!-- 图表3：审核趋势 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-header">
            <el-icon class="chart-icon pink"><Tickets /></el-icon>
            <span class="chart-title">审核记录 · 近30天</span>
          </div>
          <div class="chart-container" ref="chartReviewRef"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import {
  Grid,
  TrendCharts,
  User,
  Files,
  DocumentCopy,
  FolderOpened,
  ChatDotSquare,
  Tickets,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

// --- 数据类型定义 ---
interface StatsOverview {
  knowledgeBaseTotal: number
  documentTotal: number
  documentReady: number
  documentPendingReview: number
  documentFailed: number
  chunkTotal: number
  chunkPendingVector: number
  chunkReadyVector: number
  taskQueued: number
  taskRunning: number
  taskSuccess: number
  taskFailed: number
  taskSuccessRate: number
  reviewApproved: number
  reviewRejected: number
  documentTrend: TrendData[]
  taskTrend: TrendData[]
  reviewTrend: TrendData[]
  knowledgeBaseDistribution: KnowledgeBaseDistribution[]
  documentStatusDistribution: DocumentStatusDistribution[]
  taskStatusDistribution: TaskStatusDistribution[]
}

interface TrendData {
  date: string
  count: number
  type: string
}

interface KnowledgeBaseDistribution {
  category: string
  count: number
}

interface DocumentStatusDistribution {
  status: string
  statusName: string
  count: number
}

interface TaskStatusDistribution {
  status: string
  statusName: string
  count: number
}

// --- 状态 ---
const statsData = ref([
  { label: '知识库', value: '0', color: '#8B5CF6', icon: FolderOpened },
  { label: '文档总数', value: '0', color: '#3B82F6', icon: DocumentCopy },
  { label: '已就绪', value: '0', color: '#10B981', icon: Files },
  { label: '待审核', value: '0', color: '#F59E0B', icon: Tickets },
  { label: '失败文档', value: '0', color: '#EF4444', icon: Grid },
  { label: '切片总数', value: '0', color: '#8B5CF6', icon: Files },
  { label: '处理中', value: '0', color: '#EAB308', icon: ChatDotSquare },
])

// --- 图表实例 ---
const chartUploadRef = ref(null)
const chartTaskRef = ref(null)
const chartReviewRef = ref(null)
let chartUpload: echarts.ECharts | null = null
let chartTask: echarts.ECharts | null = null
let chartReview: echarts.ECharts | null = null

// --- API 调用 ---
const API_BASE = '/api/v1/stats'
const DAYS = 30

const fetchStats = async () => {
  try {
    const response = await axios.get(`${API_BASE}/overview`, {
      params: { days: DAYS }
    })

    if (response.data.code === 0) {
      const data: StatsOverview = response.data.data
      updateStatsData(data)
      updateCharts(data)
    } else {
      ElMessage.error(response.data.message || '获取统计数据失败')
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
    ElMessage.error('获取统计数据失败，请稍后重试')
  }
}

// --- 更新统计数据 ---
const updateStatsData = (data: StatsOverview) => {
  const stats = statsData.value

  // 知识库
  stats[0].value = formatNumber(data.knowledgeBaseTotal || 0)
  // 文档总数
  stats[1].value = formatNumber(data.documentTotal || 0)
  // 已就绪
  stats[2].value = formatNumber(data.documentReady || 0)
  // 待审核
  stats[3].value = formatNumber(data.documentPendingReview || 0)
  // 失败文档
  stats[4].value = formatNumber(data.documentFailed || 0)
  // 切片总数
  stats[5].value = formatNumber(data.chunkTotal || 0)
  // 处理中 (排队中 + 运行中)
  const processing = (data.taskQueued || 0) + (data.taskRunning || 0)
  stats[6].value = formatNumber(processing)
}

// --- 更新图表 ---
const updateCharts = (data: StatsOverview) => {
  // 1. 文档上传趋势
  if (chartUpload && data.documentTrend) {
    const dates = data.documentTrend.map(item => formatDate(item.date))
    const counts = data.documentTrend.map(item => item.count)
    chartUpload.setOption({
      xAxis: { data: dates },
      series: [{ data: counts }]
    }, true)
  }

  // 2. 任务处理趋势
  if (chartTask && data.taskTrend) {
    const dates = data.taskTrend.map(item => formatDate(item.date))
    const counts = data.taskTrend.map(item => item.count)
    chartTask.setOption({
      xAxis: { data: dates },
      series: [{ data: counts }]
    }, true)
  }

  // 3. 审核趋势 (包含通过和驳回)
  if (chartReview && data.reviewTrend) {
    const reviewData = data.reviewTrend
    // 按日期分组，分别提取通过和驳回数据
    const dateMap = new Map<string, { approved: number; rejected: number }>()
    reviewData.forEach(item => {
      const dateKey = formatDate(item.date)
      if (!dateMap.has(dateKey)) {
        dateMap.set(dateKey, { approved: 0, rejected: 0 })
      }
      const entry = dateMap.get(dateKey)!
      if (item.type === 'REVIEW_APPROVED') {
        entry.approved += item.count
      } else if (item.type === 'REVIEW_REJECTED') {
        entry.rejected += item.count
      }
    })

    const dates = Array.from(dateMap.keys())
    const approvedData = dates.map(d => dateMap.get(d)?.approved || 0)
    const rejectedData = dates.map(d => dateMap.get(d)?.rejected || 0)

    chartReview.setOption({
      xAxis: { data: dates },
      series: [
        {
          name: '通过',
          data: approvedData,
          itemStyle: { color: '#10B981' }
        },
        {
          name: '驳回',
          data: rejectedData,
          itemStyle: { color: '#EF4444' }
        }
      ]
    }, true)
  }
}

// --- 工具函数 ---
const formatNumber = (num: number): string => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + 'w'
  }
  return num.toString()
}

const formatDate = (dateStr: string): string => {
  const date = new Date(dateStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${month}-${day}`
}

// --- 初始化图表 ---
const initCharts = () => {
  // 1. 文档上传图表 (蓝色，带边框)
  if (chartUploadRef.value) {
    chartUpload = echarts.init(chartUploadRef.value)
    chartUpload.setOption({
      grid: { top: 20, right: 20, bottom: 30, left: 40 },
      tooltip: {
        trigger: 'axis',
        formatter: function(params: any) {
          const param = params[0]
          return `${param.name}<br/>上传: ${param.value} 篇`
        }
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { fontSize: 10, interval: 5 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
        axisLabel: { fontSize: 10 },
        minInterval: 1,
      },
      series: [
        {
          type: 'bar',
          data: [],
          itemStyle: { color: '#3B82F6', borderRadius: [4, 4, 0, 0] },
          barWidth: '60%',
        },
      ],
    })
  }

  // 2. 任务处理图表 (黄色)
  if (chartTaskRef.value) {
    chartTask = echarts.init(chartTaskRef.value)
    chartTask.setOption({
      grid: { top: 20, right: 20, bottom: 30, left: 40 },
      tooltip: {
        trigger: 'axis',
        formatter: function(params: any) {
          const param = params[0]
          return `${param.name}<br/>任务: ${param.value} 个`
        }
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { fontSize: 10, interval: 5 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
        axisLabel: { fontSize: 10 },
        minInterval: 1,
      },
      series: [
        {
          type: 'bar',
          data: [],
          itemStyle: { color: '#FACC15', borderRadius: [4, 4, 0, 0] },
          barWidth: '60%',
        },
      ],
    })
  }

  // 3. 审核记录图表 (粉色，堆叠柱状图)
  if (chartReviewRef.value) {
    chartReview = echarts.init(chartReviewRef.value)
    chartReview.setOption({
      grid: { top: 20, right: 20, bottom: 30, left: 40 },
      tooltip: {
        trigger: 'axis',
        formatter: function(params: any) {
          let html = `${params[0].name}<br/>`
          let total = 0
          params.forEach((p: any) => {
            html += `${p.marker} ${p.seriesName}: ${p.value}<br/>`
            total += p.value
          })
          html += `合计: ${total} 条`
          return html
        }
      },
      legend: {
        data: ['通过', '驳回'],
        fontSize: 10,
        bottom: 0,
        left: 'center',
        icon: 'circle',
        itemWidth: 8,
        itemHeight: 8,
      },
      xAxis: {
        type: 'category',
        data: [],
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { fontSize: 10, interval: 5 },
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
        axisLabel: { fontSize: 10 },
        minInterval: 1,
      },
      series: [
        {
          name: '通过',
          type: 'bar',
          stack: 'total',
          data: [],
          itemStyle: { color: '#10B981', borderRadius: [0, 0, 0, 0] },
          barWidth: '60%',
        },
        {
          name: '驳回',
          type: 'bar',
          stack: 'total',
          data: [],
          itemStyle: { color: '#EF4444', borderRadius: [4, 4, 0, 0] },
          barWidth: '60%',
        },
      ],
    })
  }
}

// --- 窗口自适应 ---
const handleResize = () => {
  chartUpload?.resize()
  chartTask?.resize()
  chartReview?.resize()
}

// --- 生命周期 ---
onMounted(() => {
  nextTick(() => {
    initCharts()
    fetchStats()
    window.addEventListener('resize', handleResize)
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartUpload?.dispose()
  chartTask?.dispose()
  chartReview?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: 100vh;
  font-family:
      -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 头部标题 */
.section-header {
  margin-bottom: 16px;
}
.title-group {
  display: flex;
  align-items: center;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.header-icon {
  margin-right: 8px;
  font-size: 20px;
}
.subtitle {
  font-size: 13px;
  color: #6b7280;
  margin-top: 4px;
  margin-left: 28px;
}
.mt-4 {
  margin-top: 32px;
}

/* 统计卡片 */
.stat-cards-row .el-col {
  margin-bottom: 16px;
}
.stat-card {
  background-color: #f3f4f6;
  border-radius: 12px;
  padding: 16px 20px;
  height: 80px;
  display: flex;
  align-items: center;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}
.stat-content {
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.stat-number {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #4b5563;
  margin-top: 4px;
}
.stat-icon {
  font-size: 18px;
  color: #9ca3af;
}

/* 图表卡片 */
.chart-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  height: 320px;
  display: flex;
  flex-direction: column;
}
.highlight-border {
  border: 2px solid #ef4444;
}
.chart-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.chart-icon {
  margin-right: 8px;
  font-size: 18px;
}
.chart-icon.yellow {
  color: #facc15;
}
.chart-icon.blue {
  color: #3b82f6;
}
.chart-icon.pink {
  color: #f472b6;
}
.chart-title {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}
.chart-container {
  flex: 1;
  width: 100%;
  height: 100%;
}
</style>
