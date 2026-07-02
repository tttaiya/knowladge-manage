<template>
  <GlassCard class="generation-view" variant="panel">
    <template #header>
      <PowerSectionTitle
        title="正文生成"
        subtitle="启动后端生成任务，并按整合版进度接口刷新章节状态。"
      >
        <template #actions>
          <ReportIdSelect v-model="localReportId" @change="refreshProgress" />
          <GlowButton @click="refreshProgress">刷新进度</GlowButton>
          <GlowButton :loading="starting" @click="startGeneration">开始生成</GlowButton>
          <GlowButton :class="polling ? 'generation-action--warn' : ''" @click="togglePolling">
            {{ polling ? '停止轮询' : '自动刷新' }}
          </GlowButton>
          <GlowButton @click="$emit('go-editor')">进入编辑</GlowButton>
        </template>
      </PowerSectionTitle>
    </template>

    <div class="generation-body">
      <section class="generation-steps" aria-label="生成进度概览">
        <div class="generation-step">
          <span class="generation-step__label">总章节</span>
          <strong>{{ progress.totalChapter || 0 }}</strong>
        </div>
        <div class="generation-step">
          <span class="generation-step__label">已完成</span>
          <strong>{{ progress.finishedChapter || 0 }}</strong>
        </div>
        <div class="generation-step generation-step--status">
          <span class="generation-step__label">当前状态</span>
          <strong>
            <span class="status-dot" :class="statusClass"></span>
            {{ statusText }}
          </strong>
        </div>
      </section>

      <section class="generation-timeline" aria-label="生成时间线">
        <div class="generation-timeline__rail">
          <span class="generation-timeline__dot generation-timeline__dot--active"></span>
          <span class="generation-timeline__line"></span>
          <span class="generation-timeline__dot" :class="{ 'generation-timeline__dot--done': finished }"></span>
        </div>
        <div class="generation-timeline__content">
          <div class="generation-timeline__item">
            <span class="generation-timeline__title">当前章节</span>
            <strong>{{ progress.currentChapterTitle || '暂无当前章节' }}</strong>
          </div>
          <div class="generation-timeline__item">
            <span class="generation-timeline__title">阶段信息</span>
            <p>{{ progress.message || '等待生成任务' }}</p>
          </div>
          <el-progress :percentage="percent" :status="progressStatus" />
        </div>
      </section>

      <section class="generation-log" aria-label="生成日志">
        <div class="generation-log__header">
          <span>运行日志</span>
          <span>{{ polling ? '自动刷新中' : '手动刷新' }}</span>
        </div>
        <ul class="generation-log__list">
          <li class="generation-log__item generation-log__item--primary">
            <span class="generation-log__time">NOW</span>
            <span class="generation-log__text">{{ progress.currentChapterTitle || '等待生成任务开始' }}</span>
          </li>
          <li class="generation-log__item">
            <span class="generation-log__time">STATUS</span>
            <span class="generation-log__text">{{ progress.message || '暂无日志详情' }}</span>
          </li>
          <li class="generation-log__item">
            <span class="generation-log__time">PROGRESS</span>
            <span class="generation-log__text">{{ percent }}%</span>
          </li>
        </ul>
      </section>
    </div>
  </GlassCard>
</template>

<script setup>
import { computed, onUnmounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import GlowButton from './common/GlowButton.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';
import ReportIdSelect from './common/ReportIdSelect.vue';

const props = defineProps({ reportId: Number });
const emit = defineEmits(['update:reportId', 'go-editor']);
const localReportId = ref(props.reportId || 1);
const progress = ref({});
const starting = ref(false);
const polling = ref(false);
let pollTimer;

watch(
  () => props.reportId,
  (value) => {
    if (value) {
      localReportId.value = value;
      refreshProgress();
    }
  },
  { immediate: true }
);

watch(localReportId, (value) => emit('update:reportId', value));
onUnmounted(stopPolling);

const percent = computed(() => {
  const total = progress.value.totalChapter || 0;
  const done = progress.value.finishedChapter || 0;
  return total ? Math.min(100, Math.round((done / total) * 100)) : 0;
});

const finished = computed(() => {
  const total = progress.value.totalChapter || 0;
  const done = progress.value.finishedChapter || 0;
  return total > 0 && done >= total;
});

const failed = computed(() => progress.value.status === 2 && !finished.value);

const statusText = computed(() => {
  if (failed.value) return '生成失败';
  if (finished.value || progress.value.status === 1) return '生成完成';
  if (progress.value.status === 0) return '未开始';
  return '未开始';
});

const progressStatus = computed(() => {
  if (failed.value) return 'exception';
  if (finished.value || progress.value.status === 1) return 'success';
  return undefined;
});

const statusClass = computed(() => {
  if (failed.value) return 'danger';
  if (finished.value || progress.value.status === 1) return 'success';
  return 'pending';
});

async function startGeneration() {
  starting.value = true;
  try {
    progress.value = await reportApi.startGeneration(localReportId.value);
    ElMessage.success('生成任务已启动，请关注进度变化');
    startPolling();
  } catch (error) {
    ElMessage.error(`启动失败：${error.message}`);
  } finally {
    starting.value = false;
  }
}

async function refreshProgress() {
  try {
    progress.value = await reportApi.getProgress(localReportId.value);
    if (polling.value && (finished.value || failed.value)) {
      stopPolling();
    }
  } catch (error) {
    ElMessage.warning(`获取进度失败：${error.message}`);
    stopPolling();
  }
}

function startPolling() {
  stopPolling();
  polling.value = true;
  pollTimer = window.setInterval(refreshProgress, 2500);
}

function stopPolling() {
  polling.value = false;
  if (pollTimer) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

function togglePolling() {
  if (polling.value) {
    stopPolling();
    return;
  }
  startPolling();
  refreshProgress();
}
</script>

<style scoped>
.generation-body {
  display: grid;
  gap: 18px;
}

.generation-steps {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.generation-step {
  padding: 16px 18px;
  border-radius: 16px;
  background: rgba(7, 18, 31, 0.72);
  border: 1px solid rgba(125, 186, 255, 0.16);
  box-shadow: inset 0 0 24px rgba(61, 139, 255, 0.05);
}

.generation-step--status strong {
  display: flex;
  align-items: center;
  gap: 10px;
}

.generation-step__label {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--pt-text-secondary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.generation-step strong {
  font-size: 24px;
  color: var(--pt-text-primary);
}

.generation-report-input {
  width: 110px;
}

.generation-action--warn :deep(.el-button) {
  background: linear-gradient(135deg, #f59e0b 0%, #ef4444 100%);
}

.generation-timeline {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 16px;
  padding: 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(5, 12, 24, 0.95), rgba(10, 20, 34, 0.92));
  border: 1px solid rgba(61, 139, 255, 0.18);
}

.generation-timeline__rail {
  display: grid;
  grid-template-rows: auto 1fr auto;
  justify-items: center;
  align-items: stretch;
  padding-top: 6px;
}

.generation-timeline__dot {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: rgba(148, 163, 184, 0.35);
  box-shadow: 0 0 16px rgba(61, 139, 255, 0.18);
}

.generation-timeline__dot--active {
  background: linear-gradient(135deg, #37f2b1 0%, #3d8bff 100%);
}

.generation-timeline__dot--done {
  background: #1f8a70;
}

.generation-timeline__line {
  width: 2px;
  margin: 10px 0;
  background: linear-gradient(180deg, rgba(61, 139, 255, 0.7), rgba(61, 139, 255, 0.08));
}

.generation-timeline__content {
  display: grid;
  gap: 14px;
}

.generation-timeline__item {
  display: grid;
  gap: 6px;
}

.generation-timeline__title {
  font-size: 12px;
  color: var(--pt-text-secondary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.generation-timeline__item strong,
.generation-timeline__item p {
  margin: 0;
}

.generation-log {
  padding: 18px 20px;
  border-radius: 18px;
  background: #060b14;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.generation-log__header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--pt-text-secondary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.generation-log__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.generation-log__item {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(13, 19, 30, 0.85);
  border: 1px solid rgba(61, 139, 255, 0.1);
}

.generation-log__item--primary {
  border-color: rgba(55, 242, 177, 0.25);
  box-shadow: inset 0 0 18px rgba(55, 242, 177, 0.04);
}

.generation-log__time {
  font-size: 12px;
  color: #67e8f9;
  letter-spacing: 0.08em;
}

.generation-log__text {
  color: var(--pt-text-primary);
}

.status-dot.pending {
  background: #e6a23c;
}

.status-dot.success {
  background: #1f8a70;
}

.status-dot.danger {
  background: #f56c6c;
}

@media (max-width: 960px) {
  .generation-steps {
    grid-template-columns: 1fr;
  }

  .generation-timeline {
    grid-template-columns: 1fr;
  }

  .generation-timeline__rail {
    grid-template-columns: auto 1fr auto;
    grid-template-rows: 1fr;
    gap: 10px;
    padding-top: 0;
  }

  .generation-timeline__line {
    width: 100%;
    height: 2px;
    margin: 0;
  }

  .generation-log__item {
    grid-template-columns: 1fr;
  }
}
</style>



