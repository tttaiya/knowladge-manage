<template>
  <GlassCard class="create-layout" variant="panel">
    <template #header>
      <PowerSectionTitle
        :icon="CreateDraftIcon"
        title="创建报告草稿"
        subtitle="支持上传 DOCX 模板并保存到模板库"
      />
    </template>

    <div class="dark-form">
      <section class="create-section">
        <h3>模板导入</h3>
        <div class="create-grid create-grid--base">
          <el-form :model="templateDraft" label-width="110px" class="create-form" label-position="top">
            <el-row :gutter="16">
              <el-col :xs="24" :md="12">
                <el-form-item label="选择模板库模板">
                  <el-select
                    v-model="form.templateId"
                    class="dark-input full-width"
                    clearable
                    filterable
                    placeholder="选择已有模板"
                  >
                    <el-option
                      v-for="(template, index) in templates"
                      :key="template.id"
                      :label="templateDisplayNames[index] || template.templateName"
                      :value="template.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="上传模板">
                  <el-upload
                    class="template-upload"
                    :auto-upload="false"
                    :limit="1"
                    :show-file-list="true"
                    accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    :on-change="handleTemplateFileChange"
                    :on-remove="handleTemplateFileRemove"
                  >
                    <GlowButton>选择模板文件</GlowButton>
                  </el-upload>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="模板名称">
                  <el-input v-model="templateDraft.templateName" class="dark-input" placeholder="保存到模板库时使用" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="24">
                <el-form-item label="章节树预览">
                  <el-input
                    v-model="templatePreview"
                    class="dark-input"
                    type="textarea"
                    :rows="6"
                    placeholder="上传模板后显示章节树预览"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </section>

      <section class="create-section">
        <h3>基础信息</h3>
        <div class="create-grid create-grid--base">
          <el-form :model="form" label-width="110px" class="create-form" label-position="top">
            <el-row :gutter="16">
              <el-col :xs="24" :md="12">
                <el-form-item label="报告主题">
                  <el-input v-model="form.theme" class="dark-input" placeholder="请输入报告主题" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="报告类型">
                  <el-select v-model="form.reportType" class="dark-input full-width" placeholder="请选择报告类型">
                    <el-option label="迎峰度夏检查" value="PEAK_SUMMER" />
                    <el-option label="煤库存审计" value="COAL_INVENTORY" />
                    <el-option label="技术方案" value="TECHNICAL_PLAN" />
                    <el-option label="调研分析" value="RESEARCH_REPORT" />
                    <el-option label="周报汇报" value="WEEKLY_REPORT" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="固定模板">
                  <el-switch v-model="fixedTemplate" active-text="是" inactive-text="否" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="当前模板">
                  <el-input v-model="currentTemplateLabel" class="dark-input" disabled />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </section>

      <section class="create-section">
        <h3>高级配置</h3>
        <div class="create-grid create-grid--advanced">
          <el-form :model="form" label-width="110px" class="create-form" label-position="top">
            <el-row :gutter="16">
              <el-col :xs="24" :md="8">
                <el-form-item label="专业领域">
                  <el-input v-model="form.major" class="dark-input" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="所属电厂">
                  <el-input v-model="form.powerPlant" class="dark-input" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="8">
                <el-form-item label="报告年份">
                  <el-input-number v-model="form.reportYear" class="dark-input full-width" :min="2000" :max="2100" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </div>
      </section>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="上传模板后可以直接保存到模板库；生成报告时会优先使用选中的模板。"
      />
    </div>

    <template #footer>
      <div class="create-footer">
        <GlowButton :loading="savingTemplate" @click="saveUploadedTemplate">保存模板到库</GlowButton>
        <GlowButton :loading="loading" @click="createDraft">创建草稿并生成大纲</GlowButton>
      </div>
    </template>
  </GlassCard>
</template>

<script setup>
import { computed, h, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import { appendDuplicateSuffix, buildNumberedDisplayNames } from '@/utils/report/templateName';
import GlowButton from './common/GlowButton.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';

const emit = defineEmits(['created']);
const loading = ref(false);
const savingTemplate = ref(false);
const templates = ref([]);
const templateFile = ref(null);
const templatePreview = ref('');
const fixedTemplate = ref(true);
const CreateDraftIcon = {
  render() {
    return h('i', { class: 'ri-file-add-line' });
  },
};
const templateDraft = reactive({
  templateName: '',
  reportType: 'TECHNICAL_PLAN',
  description: '',
  templateScope: 'GLOBAL'
});
const form = reactive({
  theme: '',
  templateId: undefined,
  reportType: 'TECHNICAL_PLAN',
  major: '电力技术监督',
  powerPlant: '示例电厂',
  reportYear: 2026,
  fixedTemplate: 1
});

const templateDisplayNames = computed(() => buildNumberedDisplayNames(templates.value));
const currentTemplateLabel = computed(() => {
  const selectedIndex = templates.value.findIndex((template) => template.id === form.templateId);
  if (selectedIndex < 0) return '未选择';
  return templateDisplayNames.value[selectedIndex] || templates.value[selectedIndex]?.templateName || '未选择';
});

onMounted(loadTemplates);

async function loadTemplates() {
  try {
    templates.value = await reportApi.listTemplates();
  } catch (error) {
    ElMessage.warning(`模板接口暂不可用：${error.message}`);
  }
}

function readFileText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('无法读取模板文件'));
    reader.readAsText(file, 'utf-8');
  });
}

function normalizeTemplateTree(nodes) {
  if (!Array.isArray(nodes)) {
    return [];
  }
  return nodes.map((node, index) => ({
    chapterTitle: node.chapterTitle || node.title || `章节 ${index + 1}`,
    chapterType: node.chapterType || 'TEXT',
    level: node.level || 1,
    sort: node.sort || index + 1,
    defaultContent: node.defaultContent || '',
    writingPrompt: node.writingPrompt || '',
    children: normalizeTemplateTree(node.children || [])
  }));
}

function buildTemplatePreview(chapters, depth = 0) {
  if (!chapters.length) {
    return '';
  }
  return chapters.map((chapter) => {
    const prefix = '  '.repeat(depth);
    const line = `${prefix}- ${chapter.chapterTitle}`;
    const childLines = buildTemplatePreview(chapter.children || [], depth + 1);
    return childLines ? `${line}\n${childLines}` : line;
  }).join('\n');
}

async function handleTemplateFileChange(uploadFile) {
  templateFile.value = uploadFile.raw;
  try {
    const text = await readFileText(uploadFile.raw);
    const parsed = JSON.parse(text);
    const chapters = normalizeTemplateTree(parsed.chapters || parsed.data || parsed);
    templatePreview.value = buildTemplatePreview(chapters) || '未解析到章节树';
    templateDraft.templateName = parsed.templateName || uploadFile.name.replace(/\.[^.]+$/, '');
    templateDraft.description = parsed.description || '通过上传模板导入';
    form.templateId = undefined;
    ElMessage.success('模板文件已加载，请先保存到模板库');
  } catch (error) {
    templatePreview.value = '';
    templateFile.value = null;
    ElMessage.warning(`模板文件解析失败：${error.message}`);
  }
}

function handleTemplateFileRemove() {
  templateFile.value = null;
  templatePreview.value = '';
}

async function saveUploadedTemplate() {
  if (!templateDraft.templateName.trim()) {
    ElMessage.warning('请先填写模板名称');
    return;
  }

  savingTemplate.value = true;
  try {
    const text = await readFileText(templateFile.value);
    const parsed = JSON.parse(text);
    const chapters = normalizeTemplateTree(parsed.chapters || parsed.data || parsed);
    const templateId = await reportApi.createTemplate({
      templateName: templateDraft.templateName,
      reportType: templateDraft.reportType,
      description: templateDraft.description,
      templateScope: templateDraft.templateScope,
      status: 1,
      chapterCount: chapters.length,
      originalFileName: templateFile.value.name,
      fileUrl: '',
      fileSize: templateFile.value.size
    });
    await reportApi.uploadTemplateFile(templateId, templateFile.value);
    await loadTemplates();
    form.templateId = templateId;
    ElMessage.success('模板已保存到模板库并可直接用于创建报告');
  } catch (error) {
    ElMessage.error(
      '保存模板失败：' + error.message
    );
  } finally {
    savingTemplate.value = false;
  }
}

async function createDraft() {
  if (!form.theme.trim()) {
    ElMessage.warning('请先填写报告主题');
    return;
  }

  loading.value = true;
  try {
    const reportId = await reportApi.createDraft({
      ...form,
      fixedTemplate: fixedTemplate.value ? 1 : 0
    });
    ElMessage.success(`草稿创建成功，报告ID：${reportId}`);
    emit('created', reportId);
  } catch (error) {
    ElMessage.error(`创建失败：${error.message}`);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.create-layout {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dark-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.create-section {
  padding: 18px 18px 6px;
  border: 1px solid rgba(80, 187, 255, 0.14);
  border-radius: 16px;
  background: rgba(4, 11, 22, 0.45);
}

.create-section h3 {
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 700;
  color: var(--pt-text-primary);
}

.create-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.create-grid {
  display: grid;
  gap: 16px;
}

.create-grid--base,
.create-grid--advanced {
  grid-template-columns: 1fr;
}

.create-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.template-upload :deep(.el-upload) {
  width: 100%;
}

.full-width {
  width: 100%;
}

@media (min-width: 768px) {
  .create-section {
    padding: 20px 20px 8px;
  }
}
</style>


