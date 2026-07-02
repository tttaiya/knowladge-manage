<template>
  <GlassCard class="outline-manager" variant="panel">
    <template #header>
      <PowerSectionTitle
        :icon="OutlineIcon"
        title="大纲管理"
        subtitle="支持查看、增删改、重新生成和保存整份大纲"
      >
        <template #actions>
          <div class="toolbar">
            <ReportIdSelect v-model="localReportId" @change="load" />
            <GlowButton @click="load">加载</GlowButton>
            <GlowButton @click="addItem">新增章节</GlowButton>
            <GlowButton @click="regenerate">重新生成</GlowButton>
            <GlowButton @click="$emit('go-generation')">去生成正文</GlowButton>
          </div>
        </template>
      </PowerSectionTitle>
    </template>

    <div class="outline-body">
      <el-table :data="items" border row-key="id" class="glass-table">
        <el-table-column prop="chapterNo" label="编号" width="110">
          <template #default="{ row }">
            <el-input v-model="row.chapterNo" class="thin-input" />
          </template>
        </el-table-column>
        <el-table-column prop="chapterTitle" label="章节标题" min-width="220">
          <template #default="{ row }">
            <el-input v-model="row.chapterTitle" class="thin-input" />
          </template>
        </el-table-column>
        <el-table-column label="层级" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.level" :min="1" :max="4" class="thin-input" />
          </template>
        </el-table-column>
        <el-table-column label="排序" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.sort" :min="0" class="thin-input" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag class="status-tag">{{ row.status || 'DRAFT' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <div class="outline-actions">
              <GlowButton class="outline-action" @click="saveItem(row)">保存</GlowButton>
              <GlowButton class="outline-action" @click="moveItem(row, -1)">上移</GlowButton>
              <GlowButton class="outline-action" @click="moveItem(row, 1)">下移</GlowButton>
              <GlowButton class="outline-action outline-action--danger" @click="removeItem(row)">
                删除
              </GlowButton>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <div class="actions">
        <GlowButton :loading="saving" @click="saveAll">保存整份大纲</GlowButton>
      </div>
    </template>
  </GlassCard>
</template>

<script setup>
import { h, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import GlowButton from './common/GlowButton.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';
import ReportIdSelect from './common/ReportIdSelect.vue';

const props = defineProps({ reportId: Number });
const emit = defineEmits(['update:reportId', 'go-generation']);
const localReportId = ref(props.reportId || 1);
const items = ref([]);
const saving = ref(false);
const OutlineIcon = {
  render() {
    return h('i', { class: 'ri-file-list-3-line' });
  },
};

watch(
  () => props.reportId,
  (value) => {
    if (value) {
      localReportId.value = value;
      load();
    }
  },
  { immediate: true }
);

watch(localReportId, (value) => emit('update:reportId', value));

async function load() {
  if (!localReportId.value) {
    ElMessage.warning('请先输入报告 ID');
    return;
  }
  try {
    items.value = await reportApi.getOutline(localReportId.value);
  } catch (error) {
    ElMessage.error(`大纲加载失败：${error.message}`);
  }
}

function addItem() {
  items.value.push({
    reportId: localReportId.value,
    parentId: 0,
    chapterNo: String(items.value.length + 1),
    chapterTitle: '新增章节',
    level: 1,
    sort: items.value.length + 1,
    editable: 1,
    aiGenerated: 0,
    status: 'DRAFT'
  });
}

async function saveItem(row) {
  if (!validateItem(row)) return;
  try {
    const saved = row.id
      ? await reportApi.updateOutlineItem(row.id, row)
      : await reportApi.addOutlineItem(localReportId.value, row);
    Object.assign(row, saved);
    ElMessage.success('章节已保存');
  } catch (error) {
    ElMessage.error(`保存失败：${error.message}`);
  }
}

async function saveAll() {
  if (!items.value.every(validateItem)) return;
  saving.value = true;
  try {
    const normalized = items.value
      .map((item, index) => ({
        ...item,
        reportId: localReportId.value,
        parentId: item.parentId ?? 0,
        sort: item.sort ?? index + 1
      }))
      .sort((a, b) => (a.sort || 0) - (b.sort || 0));
    items.value = await reportApi.updateOutline(localReportId.value, normalized);
    ElMessage.success('整份大纲已保存');
  } catch (error) {
    ElMessage.error(`保存失败：${error.message}`);
  } finally {
    saving.value = false;
  }
}

async function regenerate() {
  try {
    await ElMessageBox.confirm('重新生成会覆盖当前大纲，确定继续？', '确认重新生成');
    items.value = await reportApi.regenerateOutline(localReportId.value);
    ElMessage.success('大纲已重新生成');
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(`重新生成失败：${error.message}`);
  }
}

async function removeItem(row) {
  try {
    if (row.id) {
      await reportApi.deleteOutlineItem(row.id);
    }
    items.value = items.value.filter((item) => item !== row);
    ElMessage.success('章节已删除');
  } catch (error) {
    ElMessage.error(`删除失败：${error.message}`);
  }
}

async function moveItem(row, step) {
  const currentIndex = items.value.findIndex((item) => item === row);
  const targetIndex = currentIndex + step;
  if (currentIndex < 0 || targetIndex < 0 || targetIndex >= items.value.length) {
    return;
  }
  const reordered = [...items.value];
  reordered.splice(currentIndex, 1);
  reordered.splice(targetIndex, 0, row);
  reordered.forEach((item, index) => {
    item.sort = index + 1;
  });
  items.value = reordered;
  if (!items.value.some((item) => item.id)) {
    return;
  }
  try {
    items.value = await reportApi.updateOutline(localReportId.value, items.value);
    ElMessage.success('章节顺序已更新');
  } catch (error) {
    ElMessage.error(`移动失败：${error.message}`);
    await load();
  }
}

function validateItem(row) {
  if (!String(row.chapterTitle || '').trim()) {
    ElMessage.warning('章节标题不能为空');
    return false;
  }
  if (!String(row.chapterNo || '').trim()) {
    ElMessage.warning('章节编号不能为空');
    return false;
  }
  return true;
}
</script>

<style scoped>
.outline-manager {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.outline-body {
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.report-id-input {
  width: 130px;
}

.thin-input {
  width: 100%;
}

.thin-input :deep(.el-input__wrapper),
.thin-input :deep(.el-input-number__decrease),
.thin-input :deep(.el-input-number__increase) {
  background: rgba(4, 11, 22, 0.7);
  border-color: rgba(80, 187, 255, 0.22);
  box-shadow: none;
}

.thin-input :deep(.el-input__inner) {
  color: var(--pt-text-primary);
}

.glass-table :deep(.el-table__inner-wrapper),
.glass-table :deep(.el-table),
.glass-table :deep(.el-table__header-wrapper),
.glass-table :deep(.el-table__body-wrapper) {
  background: rgba(4, 11, 22, 0.45);
}

.glass-table :deep(.el-table__inner-wrapper::before) {
  display: none;
}

.glass-table :deep(.el-table__header th) {
  background: rgba(12, 24, 42, 0.92);
  color: var(--pt-text-primary);
  border-bottom: 1px solid rgba(80, 187, 255, 0.14);
}

.glass-table :deep(.el-table__row td) {
  background: rgba(4, 11, 22, 0.28);
  border-bottom: 1px solid rgba(80, 187, 255, 0.1);
}

.glass-table :deep(.el-table__row:hover > td) {
  background: rgba(26, 58, 92, 0.42) !important;
}

.status-tag {
  background: rgba(55, 242, 177, 0.12);
  border-color: rgba(55, 242, 177, 0.3);
  color: #8cf7d0;
}

.outline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.outline-action :deep(.el-button) {
  padding: 8px 12px;
  border-radius: 12px;
  text-transform: none;
  letter-spacing: 0;
}

.outline-action--danger :deep(.el-button) {
  background: linear-gradient(135deg, #ff6d7a 0%, #ff3d55 100%);
  color: #fff;
}

.actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .toolbar {
    width: 100%;
  }

  .report-id-input {
    width: 100%;
  }
}
</style>



