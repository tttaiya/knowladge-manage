<template>
  <div class="dashboard">
    <PowerSectionTitle
      title="后台概览"
      subtitle="展示报告模板、生成记录、成功/失败数量和近 30 天趋势"
    >
      <template #actions>
        <GlowButton :loading="loading" @click="load">刷新</GlowButton>
      </template>
    </PowerSectionTitle>

    <div class="metrics-grid">
      <MetricTile
        v-for="item in cards"
        :key="item.label"
        :label="item.label"
        :value="item.value"
        :hint="item.hint"
      />
    </div>

    <GlassCard class="chart-card" variant="section">
      <template #header>
        <div class="chart-card__header">
          <div>
            <h3>近期报告生成趋势</h3>
            <p>基于过去 30 天数据的报告生成活跃度表现</p>
          </div>
        </div>
      </template>

      <div ref="chartRef" class="trend-chart"></div>
    </GlassCard>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import { reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import GlowButton from './common/GlowButton.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';
import MetricTile from './dashboard/MetricTile.vue';

const overview = ref({});
const trend = ref([]);
const chartRef = ref();
const loading = ref(false);
let chart;

const cards = computed(() => [
  { label: '模板数量', value: overview.value.templateCount ?? 0, hint: '可用于复用的报告模板总数' },
  { label: '报告记录', value: overview.value.reportRecordCount ?? 0, hint: '包含所有生成与查询记录' },
  { label: '生成成功', value: overview.value.successRecordCount ?? 0, hint: '表示当前已正常生成的报告数量' },
  { label: '生成失败', value: overview.value.failedRecordCount ?? 0, hint: '需要留意的异常报告数量' }
]);

onMounted(load);
onUnmounted(() => {
  chart?.dispose();
  window.removeEventListener('resize', handleResize);
});

async function load() {
  loading.value = true;
  try {
    const [overviewData, trendData] = await Promise.all([
      reportApi.dashboardOverview(),
      reportApi.dashboardTrend()
    ]);
    overview.value = overviewData || {};
    trend.value = trendData || overviewData?.trendList || [];
    await nextTick();
    renderChart();
  } catch (error) {
    ElMessage.error(`工作台数据加载失败：${error.message}`);
    renderChart();
  } finally {
    loading.value = false;
  }
}

function renderChart() {
  if (!chartRef.value) return;
  chart = chart || echarts.init(chartRef.value);
  const x = trend.value.map((item) => item.date);
  const y = trend.value.map((item) => item.count);
  const fillGradient = new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: 'rgba(61, 139, 255, 0.45)' },
    { offset: 1, color: 'rgba(61, 139, 255, 0.02)' }
  ]);

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 24, top: 36, bottom: 32, containLabel: true },
    xAxis: {
      type: 'category',
      data: x.length ? x : ['暂无数据'],
      axisLine: { lineStyle: { color: 'rgba(174, 197, 255, 0.28)' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 12 }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.14)', type: 'dashed' } },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8' }
    },
    series: [
      {
        name: '报告生成数',
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        lineStyle: { width: 3, color: '#3d8bff' },
        itemStyle: { color: '#37f2b1' },
        areaStyle: { color: fillGradient },
        data: y.length ? y : [0],
        color: '#3d8bff'
      }
    ]
  });

  window.removeEventListener('resize', handleResize);
  window.addEventListener('resize', handleResize);
}

function handleResize() {
  chart?.resize();
}
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.chart-card__header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--pt-text-primary);
}

.chart-card__header p {
  margin: 6px 0 0;
  color: var(--pt-text-secondary);
  font-size: 13px;
}

.chart-card {
  overflow: hidden;
}

.trend-chart {
  height: 360px;
}

@media (max-width: 1200px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>

