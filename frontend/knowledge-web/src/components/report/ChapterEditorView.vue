<template>
  <GlassCard class="chapter-editor" variant="section">
    <template #header>
      <PowerSectionTitle
        icon="ri-edit-2-line"
        title="报告编辑"
        subtitle="Markdown 编辑与实时预览"
      >
        <template #actions>
          <div class="chapter-editor__actions">
            <ReportIdSelect v-model="localReportId" @change="loadChapters" />
            <GlowButton @click="loadChapters">加载报告</GlowButton>
          </div>
        </template>
      </PowerSectionTitle>
    </template>

    <div class="editor-layout">
      <aside class="editor-outline">
        <el-empty v-if="!chapters.length" description="暂无章节" />
        <button
          v-for="chapter in chapters"
          :key="chapter.id"
          class="chapter-item"
          :class="{ active: selected?.id === chapter.id }"
          @click="selectChapter(chapter)"
        >
          <strong>{{ chapter.chapterNo }} {{ chapter.chapterTitle }}</strong>
          <span>{{ chapterStatus(chapter.status) }} · {{ chapter.wordCount || 0 }} 字</span>
        </button>
      </aside>

      <section class="editor-main">
        <el-empty v-if="!selected" description="请选择一个章节" />
        <template v-else>
                    <div class="editor-toolbar">
            <el-tag class="editor-toolbar__tag">{{ selected.chapterNo }}</el-tag>
            <el-input v-model="draft.remark" class="editor-toolbar__remark" placeholder="备注，可选" />
            <GlowButton @click="showInsertTableDialog">插入表格</GlowButton>
            <GlowButton @click="showInsertImageDialog">插入图片</GlowButton>
            <GlowButton @click="regenerate">AI 重新生成</GlowButton>
            <GlowButton :disabled="!selected" @click="saveChapter">保存章节</GlowButton>
            <GlowButton @click="removeChapter">删除章节</GlowButton>
          </div>

          <div class="editor-panels">
            <div class="editor-panel">
              <el-input
                v-model="draft.content"
                class="editor-textarea"
                type="textarea"
                :rows="22"
                placeholder="请输入 Markdown 正文"
              />
            </div>
            <div class="editor-panel editor-preview">
              <MarkdownPreview :content="draft.content" />
            </div>
          </div>
          <div v-if="references.length" class="reference-panel">
            <div class="reference-panel__title">引用来源</div>
            <div v-for="reference in references" :key="reference.id" class="reference-item">
              <strong>{{ reference.sourceOrder }}. {{ reference.sourceTitle || '知识来源' }}</strong>
              <span>chunk {{ reference.chunkId || '-' }} · score {{ formatScore(reference.retrievalScore) }}</span>
              <p>{{ reference.excerptSnapshot }}</p>
            </div>
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="tableDialog.visible" title="插入表格" width="540px">
      <el-form :model="tableDialog" label-width="80px">
        <el-form-item label="表格标题">
          <el-input v-model="tableDialog.title" placeholder="可选，显示在表格上方" />
        </el-form-item>
        <el-form-item label="列数">
          <el-input-number v-model="tableDialog.cols" :min="1" :max="10" @change="syncTableData" />
          <span class="ml-2 hint-text">当前 {{ tableDialog.cols }} 列</span>
        </el-form-item>
        <el-form-item label="行数（不含表头）">
          <el-input-number v-model="tableDialog.rows" :min="1" :max="20" @change="syncTableData" />
          <span class="ml-2 hint-text">当前 {{ tableDialog.rows }} 行</span>
        </el-form-item>
        <el-divider />
        <el-form-item label="表头内容">
          <div v-for="(col, idx) in tableDialog.cols" :key="'h-' + idx" class="table-input-row">
            <span class="table-input-index">列{{ idx + 1 }}</span>
            <el-input v-model="tableDialog.headers[idx]" :placeholder="'列' + (idx + 1)" />
          </div>
        </el-form-item>
        <el-divider />
        <el-form-item label="数据内容">
          <div v-for="(row, ri) in tableDialog.rows" :key="'r-' + ri" class="table-input-row">
            <span class="table-input-index">行{{ ri + 1 }}</span>
            <div class="table-cells">
              <el-input
                v-for="(col, ci) in tableDialog.cols"
                :key="'c-' + ci"
                v-model="tableDialog.rowData[ri][ci]"
                :placeholder="'数据' + (ri + 1) + '-' + (ci + 1)"
              />
            </div>
          </div>
        </el-form-item>
        <el-divider />
        <el-form-item label="预览">
          <div class="table-preview-box">
            <table class="table-preview">
              <thead>
                <tr>
                  <th v-for="(h, idx) in tableHeaders" :key="'ph-' + idx">{{ h }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, ri) in tablePreviewRows" :key="'pr-' + ri">
                  <td v-for="(cell, ci) in row" :key="'pc-' + ci">{{ cell }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="tableDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="insertTable">确认插入</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="imageDialog.visible" title="插入图片" width="540px">
      <el-form :model="imageDialog" label-width="80px">
        <el-form-item label="图片方式">
          <el-tabs v-model="imageDialog.mode" class="chapter-editor__tabs">
            <el-tab-pane label="本地" name="local">
              <el-upload
                class="chapter-editor__upload"
                :auto-upload="false"
                :limit="1"
                :show-file-list="true"
                accept="image/*"
                :on-change="handleLocalImageChange"
                :on-remove="handleLocalImageRemove"
              >
                <el-button>选择本地图片</el-button>
              </el-upload>
            </el-tab-pane>
            <el-tab-pane label="链接" name="url">
              <el-input v-model="imageDialog.imageUrl" placeholder="请输入图片 URL" @input="onUrlInput" />
            </el-tab-pane>
          </el-tabs>
        </el-form-item>
        <el-form-item label="图片标题">
          <el-input v-model="imageDialog.title" placeholder="可选" />
        </el-form-item>
        <el-form-item label="预览">
          <div class="image-preview-box">
            <img
              v-if="imageDialog.imageUrl && !imageDialog.previewError"
              :src="imageDialog.imageUrl"
              class="image-preview"
              alt="图片预览"
              @error="imageDialog.previewError = true"
            />
            <el-empty v-else description="暂无预览" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="imageDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="insertImage">确认插入</el-button>
      </template>
    </el-dialog>
  </GlassCard>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { reportApi } from '@/api/modules/report';
import GlassCard from './common/GlassCard.vue';
import GlowButton from './common/GlowButton.vue';
import PowerSectionTitle from './common/PowerSectionTitle.vue';
import MarkdownPreview from './MarkdownPreview.vue';
import ReportIdSelect from './common/ReportIdSelect.vue';

const props = defineProps({ reportId: Number });
const emit = defineEmits(['update:reportId']);
const localReportId = ref(props.reportId || 1);
const chapters = ref([]);
const selected = ref(null);
const references = ref([]);
const draft = reactive({ content: '', contentFormat: 'MARKDOWN', remark: '' });

const tableDialog = reactive({
  visible: false,
  title: '',
  cols: 3,
  rows: 3,
  headers: ['列1', '列2', '列3'],
  rowData: [
    ['数据1-1', '数据1-2', '数据1-3'],
    ['数据2-1', '数据2-2', '数据2-3'],
    ['数据3-1', '数据3-2', '数据3-3']
  ]
});

const imageDialog = reactive({
  visible: false,
  mode: 'local',
  imageUrl: '',
  title: '',
  previewError: false,
  localFile: null
});

const tableHeaders = computed(() => {
  const headers = [];
  for (let i = 0; i < tableDialog.cols; i++) {
    headers.push(tableDialog.headers[i] || `列${i + 1}`);
  }
  return headers;
});

const tablePreviewRows = computed(() => {
  const rows = [];
  for (let ri = 0; ri < tableDialog.rows; ri++) {
    const row = [];
    for (let ci = 0; ci < tableDialog.cols; ci++) {
      row.push(tableDialog.rowData[ri]?.[ci] || '');
    }
    rows.push(row);
  }
  return rows;
});

watch(
  () => props.reportId,
  (value) => {
    if (value) {
      localReportId.value = value;
      loadChapters();
    }
  },
  { immediate: true }
);

watch(localReportId, (value) => emit('update:reportId', value));

function selectChapter(chapter) {
  selected.value = chapter;
  draft.content = chapter.content || '';
  draft.contentFormat = chapter.contentFormat || 'MARKDOWN';
  draft.remark = chapter.remark || '';
  loadReferences(chapter.id);
}

function applyChapterUpdate(updatedChapter) {
  if (!updatedChapter) return;
  const chapterIndex = chapters.value.findIndex((chapter) => chapter.id === updatedChapter.id);
  if (chapterIndex !== -1) {
    chapters.value.splice(chapterIndex, 1, updatedChapter);
  }
  selected.value = updatedChapter;
  draft.content = updatedChapter.content || '';
  draft.contentFormat = updatedChapter.contentFormat || 'MARKDOWN';
  draft.remark = updatedChapter.remark || '';
  loadReferences(updatedChapter.id);
}

async function loadReferences(chapterId) {
  references.value = [];
  if (!chapterId) return;
  try {
    references.value = await reportApi.listChapterReferences(chapterId);
  } catch (error) {
    references.value = [];
  }
}

async function loadChapters() {
  try {
    chapters.value = await reportApi.listChapters(localReportId.value);
    if (chapters.value.length) {
      selectChapter(chapters.value[0]);
    } else {
      selected.value = null;
    }
  } catch (error) {
    ElMessage.error(`报告加载失败：${error.message}`);
  }
}

async function saveChapter() {
  if (!selected.value) return;
  try {
    const updated = await reportApi.saveChapter(selected.value.id, draft);
    applyChapterUpdate(updated);
    ElMessage.success('章节已保存');
  } catch (error) {
    ElMessage.error(`保存失败：${error.message}`);
  }
}

async function regenerate() {
  if (!selected.value) return;
  try {
    const updated = await reportApi.regenerateChapter(selected.value.id, draft);
    applyChapterUpdate(updated);
    ElMessage.success('报告已重新生成');
  } catch (error) {
    ElMessage.error(`重新生成失败：${error.message}`);
  }
}

function showInsertTableDialog() {
  if (!selected.value) return;
  resetTableDialog();
  tableDialog.visible = true;
}

function resetTableDialog() {
  tableDialog.title = '';
  tableDialog.cols = 3;
  tableDialog.rows = 3;
  tableDialog.headers = ['列1', '列2', '列3'];
  tableDialog.rowData = [
    ['数据1-1', '数据1-2', '数据1-3'],
    ['数据2-1', '数据2-2', '数据2-3'],
    ['数据3-1', '数据3-2', '数据3-3']
  ];
}

function syncTableData() {
  while (tableDialog.headers.length < tableDialog.cols) {
    tableDialog.headers.push(`列${tableDialog.headers.length + 1}`);
  }
  if (tableDialog.headers.length > tableDialog.cols) {
    tableDialog.headers.length = tableDialog.cols;
  }

  while (tableDialog.rowData.length < tableDialog.rows) {
    const newRow = [];
    for (let ci = 0; ci < tableDialog.cols; ci++) {
      newRow.push(`数据${tableDialog.rowData.length + 1}-${ci + 1}`);
    }
    tableDialog.rowData.push(newRow);
  }
  if (tableDialog.rowData.length > tableDialog.rows) {
    tableDialog.rowData.length = tableDialog.rows;
  }

  for (let ri = 0; ri < tableDialog.rowData.length; ri++) {
    while (tableDialog.rowData[ri].length < tableDialog.cols) {
      tableDialog.rowData[ri].push('');
    }
    if (tableDialog.rowData[ri].length > tableDialog.cols) {
      tableDialog.rowData[ri].length = tableDialog.cols;
    }
  }
}

async function insertTable() {
  if (!selected.value) return;
  try {
    const requestData = {
      title: tableDialog.title || undefined,
      headers: tableDialog.headers.filter((header) => header.trim() !== ''),
      rows: tableDialog.rowData.map((row) => [...row])
    };
    const updated = await reportApi.insertTable(selected.value.id, requestData);
    applyChapterUpdate(updated);
    tableDialog.visible = false;
    ElMessage.success('表格已插入');
  } catch (error) {
    ElMessage.error(`插入表格失败：${error.message}`);
  }
}

function showInsertImageDialog() {
  if (!selected.value) return;
  imageDialog.mode = 'local';
  imageDialog.imageUrl = '';
  imageDialog.title = '';
  imageDialog.previewError = false;
  imageDialog.localFile = null;
  imageDialog.visible = true;
}

function onUrlInput() {
  imageDialog.previewError = false;
  imageDialog.localFile = null;
  imageDialog.mode = 'url';
}

function handleLocalImageChange(uploadFile) {
  imageDialog.localFile = uploadFile.raw;
  imageDialog.mode = 'local';
  imageDialog.previewError = false;
  if (uploadFile.raw) {
    imageDialog.imageUrl = URL.createObjectURL(uploadFile.raw);
  }
}

function handleLocalImageRemove() {
  if (imageDialog.imageUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(imageDialog.imageUrl);
  }
  imageDialog.localFile = null;
  imageDialog.imageUrl = '';
  imageDialog.previewError = false;
  imageDialog.mode = 'local';
}

async function insertImage() {
  if (!selected.value) return;
  try {
    let imageUrl = imageDialog.imageUrl;
    if (imageDialog.localFile) {
      const uploaded = await reportApi.uploadImage(imageDialog.localFile);
      imageUrl = uploaded.fileUrl || uploaded.url || uploaded.path || uploaded.fileName;
    }
    if (!imageUrl) {
      ElMessage.warning('请输入图片 URL 或选择本地图片');
      return;
    }
    const requestData = {
      imageUrl,
      title: imageDialog.title || '图片'
    };
    const updated = await reportApi.insertImage(selected.value.id, requestData);
    applyChapterUpdate(updated);
    if (imageDialog.imageUrl?.startsWith('blob:')) {
      URL.revokeObjectURL(imageDialog.imageUrl);
    }
    imageDialog.visible = false;
    ElMessage.success('图片已插入');
  } catch (error) {
    ElMessage.error(`插入图片失败：${error.message}`);
  }
}

async function removeChapter() {
  if (!selected.value) return;
  try {
    await ElMessageBox.confirm(`确定删除报告章节“${selected.value.chapterTitle}”吗？`, '删除确认');
    await reportApi.deleteChapter(selected.value.id);
    chapters.value = chapters.value.filter((chapter) => chapter.id !== selected.value.id);
    selected.value = null;
    draft.content = '';
    draft.remark = '';
    ElMessage.success('报告章节已删除');
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(`删除失败：${error.message}`);
  }
}

function chapterStatus(status) {
  if (status === 0) return '未生成';
  if (status === 1) return '已生成';
  if (status === 2) return '失败';
  if (status === 3) return '已编辑';
  return '未知';
}

function formatScore(score) {
  if (score === null || score === undefined || score === '') return '-';
  const num = Number(score);
  return Number.isFinite(num) ? num.toFixed(4) : score;
}
</script>

<style scoped>
.chapter-editor {
  display: block;
}

.chapter-editor__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}


.chapter-editor__upload :deep(.el-upload) {
  width: 100%;
}

.chapter-editor__tabs :deep(.el-tabs__nav-wrap::after) {
  background: rgba(255, 255, 255, 0.08);
}

.chapter-editor__tabs :deep(.el-tabs__item) {
  color: var(--pt-text-secondary);
}

.chapter-editor__tabs :deep(.el-tabs__item.is-active) {
  color: var(--pt-brand);
}

.chapter-editor__tabs :deep(.el-tabs__active-bar) {
  background: var(--pt-brand);
}

.editor-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.editor-outline,
.editor-main {
  min-height: 560px;
  padding: 18px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  background: rgba(8, 15, 28, 0.56);
  backdrop-filter: blur(18px);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.editor-outline {
  overflow: auto;
}

.chapter-item {
  width: 100%;
  display: block;
  margin-bottom: 10px;
  padding: 14px 15px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.72);
  color: var(--pt-text-primary);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, transform 0.18s ease, background 0.18s ease;
}

.chapter-item:hover {
  transform: translateY(-1px);
  border-color: rgba(61, 139, 255, 0.45);
}

.chapter-item.active {
  border-color: rgba(61, 242, 177, 0.55);
  background: linear-gradient(135deg, rgba(55, 242, 177, 0.12), rgba(61, 139, 255, 0.14));
}

.chapter-item strong,
.chapter-item span {
  display: block;
}

.chapter-item span {
  margin-top: 6px;
  color: var(--pt-text-secondary);
  font-size: 12px;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.editor-toolbar__tag {
  flex: 0 0 auto;
}

.editor-toolbar__remark {
  flex: 1 1 220px;
  min-width: 180px;
}

.editor-panels {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.editor-panel {
  min-height: 520px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  border-radius: 16px;
  background: rgba(2, 8, 23, 0.48);
}

.reference-panel {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid rgba(80, 187, 255, 0.16);
  border-radius: 14px;
  background: rgba(4, 11, 22, 0.42);
}

.reference-panel__title {
  margin-bottom: 10px;
  font-weight: 700;
  color: var(--pt-text-primary);
}

.reference-item {
  display: grid;
  gap: 4px;
  padding: 10px 0;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}

.reference-item span,
.reference-item p {
  margin: 0;
  color: var(--pt-text-secondary);
  font-size: 13px;
}

.editor-textarea {
  height: 100%;
}

.editor-textarea :deep(.el-textarea__inner) {
  min-height: 520px !important;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.88);
  color: var(--pt-text-primary);
  box-shadow: none;
}

.editor-textarea :deep(.el-textarea__inner:focus) {
  border-color: rgba(61, 139, 255, 0.5);
}

.editor-preview {
  overflow: auto;
}

.editor-preview :deep(*) {
  color: var(--pt-text-primary);
}

.editor-preview :deep(img) {
  max-width: 100%;
}

.table-input-row {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
  gap: 6px;
}

.table-input-index {
  min-width: 36px;
  font-size: 13px;
  color: #667085;
  font-weight: 600;
}

.table-cells {
  display: flex;
  flex: 1;
  gap: 6px;
}

.table-cells .el-input {
  flex: 1;
}

.table-preview-box,
.image-preview-box {
  width: 100%;
  overflow-x: auto;
}

.table-preview {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.table-preview th,
.table-preview td {
  border: 1px solid #dcdfe6;
  padding: 6px 10px;
  text-align: left;
}

.table-preview th {
  background: #f5f7fa;
  font-weight: 600;
}

.image-preview {
  max-width: 100%;
  max-height: 200px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
}

.ml-2 {
  margin-left: 8px;
}

.hint-text {
  color: #909399;
  font-size: 13px;
}

@media (max-width: 980px) {
  .editor-layout {
    grid-template-columns: 1fr;
  }

  .editor-panels {
    grid-template-columns: 1fr;
  }

  .editor-outline,
  .editor-main,
  .editor-panel {
    min-height: auto;
  }

  .editor-textarea :deep(.el-textarea__inner) {
    min-height: 360px !important;
  }
}
</style>







