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
      <!-- 图表1：知识问答 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-header">
            <el-icon class="chart-icon yellow"><ChatDotSquare /></el-icon>
            <span class="chart-title">知识问答 · 近30天</span>
          </div>
          <div class="chart-container" ref="chart1Ref"></div>
        </div>
      </el-col>

      <!-- 图表2：文档上传 (截图红框部分) -->
      <el-col :xs="24" :md="8">
        <div class="chart-card highlight-border">
          <div class="chart-header">
            <el-icon class="chart-icon blue"><DocumentCopy /></el-icon>
            <span class="chart-title">文档上传 · 近30天</span>
          </div>
          <div class="chart-container" ref="chart2Ref"></div>
        </div>
      </el-col>

      <!-- 图表3：报告生成 -->
      <el-col :xs="24" :md="8">
        <div class="chart-card">
          <div class="chart-header">
            <el-icon class="chart-icon pink"><Tickets /></el-icon>
            <span class="chart-title">报告生成 · 近30天</span>
          </div>
          <div class="chart-container" ref="chart3Ref"></div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
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

// --- 顶部数据 ---
const statsData = [
  { label: '用户数', value: '1', color: '#333', icon: User },
  { label: '知识库', value: '2', color: '#8B5CF6', icon: FolderOpened }, // 紫色
  { label: '文档数', value: '72', color: '#3B82F6', icon: DocumentCopy }, // 蓝色
  { label: '切片数', value: '7358', color: '#10B981', icon: Files }, // 绿色
  { label: '报告模板', value: '7', color: '#F59E0B', icon: Tickets }, // 橙色
  { label: '报告生成', value: '0', color: '#EC4899', icon: Grid }, // 粉色
  { label: '知识问答', value: '15', color: '#EAB308', icon: ChatDotSquare }, // 黄色
]

// --- 图表实例 ---
const chart1Ref = ref(null)
const chart2Ref = ref(null)
const chart3Ref = ref(null)
let chart1, chart2, chart3

// 模拟 30 天 X 轴数据 (05-29 到 06-27)
const getXAxisData = () => {
  const dates = []
  for (let i = 29; i <= 30; i++) dates.push(`05-${i}`)
  for (let i = 1; i <= 27; i++) dates.push(`06-${String(i).padStart(2, '0')}`)
  return dates
}
const xData = getXAxisData()

// 模拟数据生成器 (模拟截图中的波峰)
const generateMockData = (peakDay, peakVal) => {
  return xData.map((_, index) => {
    if (index === xData.length - 1) return 1 // 最后的微量
    if (index === peakDay) return peakVal
    if (index === peakDay - 1) return peakVal * 0.7
    if (index === peakDay - 2) return peakVal * 0.2
    return 0
  })
}

// 初始化图表配置
const initCharts = () => {
  // 1. 知识问答 (黄色)
  chart1 = echarts.init(chart1Ref.value)
  chart1.setOption({
    grid: { top: 20, right: 20, bottom: 30, left: 30 },
    xAxis: {
      type: 'category',
      data: xData,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, interval: 6 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
      axisLabel: { fontSize: 10 },
    },
    series: [
      {
        type: 'bar',
        data: generateMockData(xData.length - 1, 14),
        itemStyle: { color: '#FACC15' },
        barWidth: 8,
      },
    ],
  })

  // 2. 文档上传 (截图红框，蓝色，有波峰)
  chart2 = echarts.init(chart2Ref.value)
  chart2.setOption({
    grid: { top: 20, right: 20, bottom: 30, left: 30 },
    xAxis: {
      type: 'category',
      data: xData,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, interval: 6 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
      axisLabel: { fontSize: 10 },
    },
    series: [
      {
        type: 'bar',
        data: generateMockData(xData.length - 5, 38),
        itemStyle: { color: '#3B82F6' },
        barWidth: 8,
      },
    ],
  })

  // 3. 报告生成 (粉色，几乎全平)
  chart3 = echarts.init(chart3Ref.value)
  chart3.setOption({
    grid: { top: 20, right: 20, bottom: 30, left: 30 },
    xAxis: {
      type: 'category',
      data: xData,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { fontSize: 10, interval: 6 },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { type: 'dashed', color: '#eee' } },
      axisLabel: { fontSize: 10 },
    },
    series: [
      { type: 'bar', data: Array(30).fill(0), itemStyle: { color: '#F472B6' }, barWidth: 8 },
    ],
  })
}

// 窗口自适应
const handleResize = () => {
  chart1?.resize()
  chart2?.resize()
  chart3?.resize()
}

onMounted(() => {
  initCharts()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart1?.dispose()
  chart2?.dispose()
  chart3?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 24px;
  background-color: #f5f7fa; /* 全局淡灰背景 */
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
  background-color: #f3f4f6; /* 卡片背景浅灰 */
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
/* 对应截图中文档上传的红框 */
.highlight-border {
  border: 2px solid #ef4444; /* 红色边框 */
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
