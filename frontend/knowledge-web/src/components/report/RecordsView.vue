<template>
  <div class="records-page">
    <GlassCard class="records-layout" variant="panel">
    <template #header>
      <PowerSectionTitle
        title="报告记录"
        subtitle="查看报告记录，进入编辑，重新导出 DOCX。"
      >
        <template #actions>
          <el-select v-model="statusFilter" class="records-select" placeholder="状态筛选">
            <el-option label="全部状态" value="all" />
            <el-option label="草稿" value="draft" />
            <el-option label="已生成" value="generated" />
            <el-option label="失败" value="failed" />
            <el-option label="已删除" value="deleted" />
          </el-select>
          <GlowButton @click="load">刷新</GlowButton>
        </template>
      </PowerSectionTitle>
    </template>

    <div class="records-grid">
      <RecordCard
        v-for="record in pagedRecords"
        :key="record.id"
        :record="record"
        @select-report="emit('select-report', $event)"
        @export-docx="exportDocx"
        @remove="remove"
      />
    </div>

    <div class="records-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="filteredRecords.length"
        layout="total, prev, pager, next, jumper"
        background
        hide-on-single-page
      />
    </div>
  </GlassCard>

  <el-dialog v-model="exportVisible" title="导出结果" width="520px">
    <el-descriptions v-if="exportResult" :column="1" border>
      <el-descriptions-item label="报告 ID">{{ exportResult.reportId }}</el-descriptions-item>
      <el-descriptions-item label="任务 ID">{{ exportResult.exportTaskId }}</el-descriptions-item>
      <el-descriptions-item label="文件名">{{ exportResult.fileName }}</el-descriptions-item>
      <el-descriptions-item label="提示">{{ exportResult.message }}</el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button @click="exportVisible = false">关闭</el-button>
      <el-button v-if="downloadUrl" type="primary" @click="download">下载 DOCX</el-button>
    </template>
  </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fileDownloadUrl, reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import GlowButton from './common/GlowButton.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';
import RecordCard from './records/RecordCard.vue';

const emit = defineEmits(['select-report']);

const records = ref([]);
const exportVisible = ref(false);
const exportResult = ref(null);
const currentPage = ref(1);
const pageSize = ref(6);
const statusFilter = ref('all');

const downloadUrl = computed(() => fileDownloadUrl(exportResult.value?.fileName || exportResult.value?.fileUrl || ''));
const filteredRecords = computed(() => {
  const list = records.value || [];
  if (statusFilter.value === 'draft') return list.filter((record) => record.status === 0);
  if (statusFilter.value === 'generated') return list.filter((record) => record.status === 1);
  if (statusFilter.value === 'failed') return list.filter((record) => record.status === 2);
  if (statusFilter.value === 'deleted') return list.filter((record) => record.status === 3);
  return list;
});
const pagedRecords = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredRecords.value.slice(start, start + pageSize.value);
});

onMounted(() => {
  load();
  window.addEventListener('report:hide-record-overlays', hideOverlays);
});

onUnmounted(() => {
  window.removeEventListener('report:hide-record-overlays', hideOverlays);
});

async function load() {
  try {
    records.value = await reportApi.listRecords();
    currentPage.value = 1;
  } catch (error) {
    ElMessage.error(`记录加载失败：${error.message}`);
  }
}

async function exportDocx(row) {
  try {
    exportResult.value = await reportApi.regenerateDocx(row.id);
    exportVisible.value = true;
    ElMessage.success('DOCX 已重新生成');
    await load();
  } catch (error) {
    ElMessage.error(`导出失败：${error.message}`);
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除报告“${row.reportName}”吗？`, '删除确认');
    await reportApi.deleteRecord(row.id);
    await load();
    ElMessage.success('报告已删除');
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`删除失败：${error.message}`);
    }
  }
}

function hideOverlays() {
  exportVisible.value = false;
  exportResult.value = null;
}

function download() {
  window.open(downloadUrl.value, '_blank');
}
</script>

<style scoped>
.records-page {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.records-layout {
  overflow: hidden;
}

.records-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.records-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.records-select {
  width: 180px;
}

.records-select :deep(.el-input__wrapper) {
  background: rgba(10, 18, 34, 0.72);
  border: 1px solid rgba(174, 197, 255, 0.18);
  box-shadow: none;
  border-radius: 14px;
}

.records-select :deep(.el-input__inner) {
  color: var(--pt-text-primary);
}

.records-select :deep(.el-select__caret) {
  color: var(--pt-text-secondary);
}

.records-select :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(61, 139, 255, 0.55);
}

.records-select :deep(.el-input__wrapper:hover) {
  border-color: rgba(61, 139, 255, 0.38);
}

:deep(.el-pagination.is-background .btn-prev),
:deep(.el-pagination.is-background .btn-next),
:deep(.el-pagination.is-background .el-pager li) {
  background: rgba(10, 18, 34, 0.72);
  color: var(--pt-text-secondary);
  border: 1px solid rgba(174, 197, 255, 0.14);
}

:deep(.el-pagination.is-background .el-pager li.is-active) {
  background: linear-gradient(135deg, #37f2b1 0%, #3d8bff 100%);
  color: #04111f;
}

@media (max-width: 1200px) {
  .records-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .records-pagination {
    justify-content: center;
  }

  .records-select {
    width: 100%;
  }
}
</style>

