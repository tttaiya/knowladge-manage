<template>
  <GlassCard class="record-card" variant="section">
    <template #header>
      <div class="record-card__header">
        <div class="record-card__title-group">
          <div class="record-card__eyebrow">记录 #{{ record.id }}</div>
          <h3 class="record-card__title">{{ record.reportName }}</h3>
          <div class="record-card__meta-line">
            <span>{{ reportTypeLabel(record.reportType) }}</span>
            <span>·</span>
            <span>{{ record.reportYear || '—' }}</span>
          </div>
          <div class="record-card__time">生成时间：{{ formatDateTime(record.createTime) }}</div>
        </div>

        <div class="record-card__badges">
          <StatusPill :variant="statusVariant">{{ statusText }}</StatusPill>
          <StatusPill :variant="exportVariant">{{ exportText }}</StatusPill>
        </div>
      </div>
    </template>

    <div class="record-card__body">
      <dl class="record-card__details">
        <div>
          <dt>专业</dt>
          <dd>{{ record.major || '未填写' }}</dd>
        </div>
        <div>
          <dt>电厂</dt>
          <dd>{{ record.powerPlant || '未填写' }}</dd>
        </div>
        <div>
          <dt>状态</dt>
          <dd>{{ statusText }}</dd>
        </div>
        <div>
          <dt>导出</dt>
          <dd>{{ exportText }}</dd>
        </div>
      </dl>
    </div>

    <template #footer>
      <div class="record-card__actions">
        <GlowButton class="record-card__action" @click="$emit('select-report', record.id)">编辑</GlowButton>
        <GlowButton class="record-card__action" @click="$emit('export-docx', record)">导出 DOCX</GlowButton>
        <GlowButton class="record-card__action record-card__action--danger" @click="$emit('remove', record)">
          删除
        </GlowButton>
      </div>
    </template>
  </GlassCard>
</template>

<script setup>
import { computed } from 'vue';
import GlassCard from '../common/GlassCard.vue';
import GlowButton from '../common/GlowButton.vue';
import StatusPill from '../common/StatusPill.vue';

const props = defineProps({
  record: {
    type: Object,
    required: true,
  },
});

defineEmits(['select-report', 'export-docx', 'remove']);

function reportTypeLabel(value) {
  if (value === 'PEAK_SUMMER') return '迎峰度夏检查';
  if (value === 'COAL_INVENTORY') return '煤库存审计';
  if (value === 'TECHNICAL_PLAN') return '技术方案';
  if (value === 'RESEARCH_REPORT') return '调研分析';
  if (value === 'WEEKLY_REPORT') return '周报汇报';
  return value || '未设置类型';
}

function formatDateTime(value) {
  if (!value) return '未记录';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return String(value);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return year + '-' + month + '-' + day + ' ' + hour + ':' + minute;
}

const statusText = computed(() => {
  if (props.record.status === 0) return '草稿';
  if (props.record.status === 1) return '已生成';
  if (props.record.status === 2) return '失败';
  if (props.record.status === 3) return '已删除';
  return '未知';
});

const exportText = computed(() => {
  if (props.record.exportStatus === 0 || props.record.exportStatus == null) return '未导出';
  if (props.record.exportStatus === 1) return '导出中';
  if (props.record.exportStatus === 2) return '导出成功';
  if (props.record.exportStatus === 3) return '导出失败';
  return '未知';
});

const statusVariant = computed(() => {
  if (props.record.status === 1) return 'success';
  if (props.record.status === 2) return 'danger';
  if (props.record.status === 0) return 'warning';
  if (props.record.status === 3) return 'offline';
  return 'info';
});

const exportVariant = computed(() => {
  if (props.record.exportStatus === 1) return 'warning';
  if (props.record.exportStatus === 2) return 'success';
  if (props.record.exportStatus === 3) return 'danger';
  if (props.record.exportStatus === 0 || props.record.exportStatus == null) return 'info';
  return 'info';
});
</script>

<style scoped>
.record-card {
  height: 100%;
}

.record-card__header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.record-card__title-group {
  min-width: 0;
}

.record-card__eyebrow {
  font-size: 12px;
  color: var(--pt-text-secondary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.record-card__title {
  margin: 0;
  font-size: 18px;
  line-height: 1.35;
  color: var(--pt-text-primary);
  word-break: break-word;
}

.record-card__meta-line {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  font-size: 13px;
  color: var(--pt-text-secondary);
}

.record-card__time {
  margin-top: 6px;
  font-size: 13px;
  color: var(--pt-text-secondary);
}

.record-card__badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.record-card__body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.record-card__details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin: 0;
}

.record-card__details div {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(10, 18, 34, 0.4);
  border: 1px solid rgba(174, 197, 255, 0.12);
}

.record-card__details dt {
  font-size: 12px;
  color: var(--pt-text-secondary);
  margin-bottom: 6px;
}

.record-card__details dd {
  margin: 0;
  color: var(--pt-text-primary);
  font-size: 14px;
  font-weight: 600;
  word-break: break-word;
}

.record-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.record-card__action :deep(.el-button) {
  min-width: 96px;
}

.record-card__action--danger :deep(.el-button) {
  background: linear-gradient(135deg, rgba(255, 107, 129, 0.92), rgba(255, 183, 85, 0.92));
  color: #16070b;
}

@media (max-width: 640px) {
  .record-card__header {
    flex-direction: column;
  }

  .record-card__badges {
    justify-content: flex-start;
  }

  .record-card__details {
    grid-template-columns: 1fr;
  }
}
</style>

