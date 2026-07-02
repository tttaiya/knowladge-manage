<template>
  <GlassCard class="templates-view" variant="panel">
    <template #header>
      <PowerSectionTitle
        title="模板管理"
        subtitle="管理报告模板，统一模板列表与编辑入口。"
      >
        <template #actions>
          <div class="toolbar">
            <GlowButton @click="load">刷新</GlowButton>
          </div>
        </template>
      </PowerSectionTitle>
    </template>

    <div class="templates-body">
      <el-table :data="templates" border row-key="id" class="glass-table">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="模板名称" min-width="220">
        <template #default="{ row, $index }">{{ templateDisplayNames[$index] || row.templateName }}</template>
      </el-table-column>
        <el-table-column label="报告类型" width="170">
        <template #default="{ row }">{{ reportTypeLabel(row.reportType) }}</template>
      </el-table-column>
        <el-table-column prop="templateScope" label="范围" width="120" />
        <el-table-column prop="chapterCount" label="章节数" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            {{ row.status === 1 ? '已发布' : '草稿' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <GlowButton class="row-action" @click="openEdit(row)">编辑</GlowButton>
              <GlowButton class="row-action row-action--danger" @click="remove(row)">删除</GlowButton>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <PowerDialog v-model="dialogVisible" title="编辑模板" width="900px">
      <el-form label-position="top" :model="form">
        <el-form-item label="模板名称">
          <el-input v-model="form.templateName" class="dark-input" />
        </el-form-item>
        <el-form-item label="报告类型">
          <el-select v-model="form.reportType" class="dark-input full-width">
            <el-option label="迎峰度夏检查" value="PEAK_SUMMER" />
            <el-option label="煤库存审计" value="COAL_INVENTORY" />
            <el-option label="技术方案" value="TECHNICAL_PLAN" />
            <el-option label="调研分析" value="RESEARCH_REPORT" />
            <el-option label="周报汇报" value="WEEKLY_REPORT" />
          </el-select>
        </el-form-item>
        <el-form-item label="可见范围">
          <el-select v-model="form.templateScope" class="dark-input full-width">
            <el-option label="全局共享" value="GLOBAL" />
            <el-option label="个人私有" value="PERSONAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :label="0">草稿</el-radio>
            <el-radio :label="1">发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" class="dark-input" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <GlowButton @click="dialogVisible = false">取消</GlowButton>
        <GlowButton @click="save">保存</GlowButton>
      </template>
    </PowerDialog>
  </GlassCard>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import GlowButton from './common/GlowButton.vue';
import PowerDialog from './common/PowerDialog.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';
import { appendDuplicateSuffix, buildNumberedDisplayNames } from '@/utils/report/templateName';

const templates = ref([]);
const dialogVisible = ref(false);
const form = reactive(createEmpty());
const templateDisplayNames = computed(() => buildNumberedDisplayNames(templates.value));

onMounted(load);

function reportTypeLabel(value) {
  if (value === 'PEAK_SUMMER') return '迎峰度夏检查';
  if (value === 'COAL_INVENTORY') return '煤库存审计';
  if (value === 'TECHNICAL_PLAN') return '技术方案';
  if (value === 'RESEARCH_REPORT') return '调研分析';
  if (value === 'WEEKLY_REPORT') return '周报汇报';
  return value || '未设置类型';
}

function createEmpty() {
  return {
    id: undefined,
    templateName: '',
    reportType: 'TECHNICAL_PLAN',
    description: '',
    status: 1,
    templateScope: 'GLOBAL',
    chapterCount: 0
  };
}

async function load() {
  try {
    templates.value = await reportApi.listTemplates();
  } catch (error) {
    ElMessage.error(`模板加载失败：${error.message}`);
  }
}

function openEdit(row) {
  Object.assign(form, createEmpty(), row);
  dialogVisible.value = true;
}

async function save() {
  if (!form.templateName.trim()) {
    ElMessage.warning('请填写模板名称');
    return;
  }
  if (!form.id) {
    form.templateName = appendDuplicateSuffix(
      form.templateName,
      templates.value.map((template) => template.templateName)
    );
  }
  try {
    await reportApi.updateTemplate(form.id, form);
    dialogVisible.value = false;
    await load();
    ElMessage.success('模板已更新');
  } catch (error) {
    ElMessage.error('保存失败：' + error.message);
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除模板“${row.templateName}”？`, '删除确认');
    await reportApi.deleteTemplate(row.id);
    await load();
    ElMessage.success('模板已删除');
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(`删除失败：${error.message}`);
  }
}
</script>

<style scoped>
.templates-view {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.templates-body {
  display: flex;
  flex-direction: column;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.row-action :deep(.el-button) {
  padding: 8px 12px;
  border-radius: 12px;
  text-transform: none;
  letter-spacing: 0;
}

.row-action--danger :deep(.el-button) {
  background: linear-gradient(135deg, #ff6d7a 0%, #ff3d55 100%);
  color: #fff;
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

@media (max-width: 768px) {
  .toolbar {
    width: 100%;
  }
}
</style>





