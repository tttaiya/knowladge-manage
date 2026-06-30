<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TableInstance } from 'element-plus'
import DocumentStatusTag from '@/components/knowledge/DocumentStatusTag.vue'
import UploadDocumentDialog from '@/components/knowledge/UploadDocumentDialog.vue'
import {
  batchDeleteDocuments,
  deleteDocument,
  fetchDocumentChunks,
  fetchDocuments,
  normalizeTags,
  updateDocumentTags,
  validateTags,
} from '@/api/modules/document'
import type { DocumentItem, DocumentStatus } from '@/types/knowledge'

const route = useRoute()
const router = useRouter()

const kbId = ref<number>(Number(route.params.kbId))
const loading = ref(false)
const documents = ref<DocumentItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const statusFilter = ref<DocumentStatus | ''>('')
const keyword = ref('')
const selectedIds = ref<number[]>([])
const uploadVisible = ref(false)
const deletingDocId = ref<number | null>(null)
const batchDeleting = ref(false)
const tableRef = ref<TableInstance>()

const tagDialogVisible = ref(false)
const editingDoc = ref<DocumentItem | null>(null)
const tagEditTags = ref<string[]>([])
const tagSaving = ref(false)

const chunkDialogVisible = ref(false)
const chunkLoading = ref(false)
const chunks = ref<{ chunkIndex: number; content: string; charCount: number }[]>([])

const statusOptions = [
  { label: '全部', value: '' },
  { label: '已上传', value: 'UPLOADED' },
  { label: '解析中', value: 'PARSING' },
  { label: '切片中', value: 'CHUNKING' },
  { label: '向量化中', value: 'VECTORIZING' },
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '就绪', value: 'READY' },
  { label: '审核未通过', value: 'REVIEW_REJECTED' },
  { label: '失败', value: 'FAILED' },
]

async function loadDocuments() {
  if (!kbId.value || Number.isNaN(kbId.value)) {
    return
  }
  loading.value = true
  try {
    const data = await fetchDocuments({
      kbId: kbId.value,
      status: statusFilter.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      pageSize: pageSize.value,
    })
    documents.value = data.records
    total.value = data.total
  } catch (error: any) {
    documents.value = []
    total.value = 0
    const message: string = error?.message ?? '加载失败'
    if (message.includes('无法连接') || message.includes('未找到')) {
      ElMessage.warning(message)
    } else {
      ElMessage.error(message)
    }
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows: DocumentItem[]) {
  selectedIds.value = rows.map((row) => row.id)
}

/** US3.7 删除单个文档：二次确认 → 逻辑删除 → 刷新列表 */
async function handleDelete(row: DocumentItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除文档「${row.originalName}」？\n\n` +
        '· 文档将从列表中移除并进入回收站（保留 30 天）\n' +
        '· 关联向量数据将同步标记失效，不再参与检索',
      '确认删除',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  deletingDocId.value = row.id
  try {
    await deleteDocument(row.id)
    ElMessage.success('文档已删除，已从列表移除')
    if (selectedIds.value.includes(row.id)) {
      selectedIds.value = selectedIds.value.filter((id) => id !== row.id)
    }
    await loadDocuments()
  } catch (error: any) {
    ElMessage.error(error?.message ?? '删除失败')
  } finally {
    deletingDocId.value = null
  }
}

/** US3.8 批量删除：勾选多篇 → 确认 → 一次性逻辑删除 */
async function handleBatchDelete() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先勾选要删除的文档')
    return
  }

  const count = selectedIds.value.length
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${count} 篇文档？\n\n` +
        '· 文档将从列表中移除并进入回收站（保留 30 天）\n' +
        '· 关联向量数据将同步标记失效，不再参与检索',
      '批量删除',
      {
        type: 'warning',
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  batchDeleting.value = true
  try {
    await batchDeleteDocuments([...selectedIds.value])
    ElMessage.success(`已成功删除 ${count} 篇文档`)
    selectedIds.value = []
    tableRef.value?.clearSelection()
    await loadDocuments()
  } catch (error: any) {
    ElMessage.error(error?.message ?? '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

function openTagDialog(row: DocumentItem) {
  editingDoc.value = row
  tagEditTags.value = row.tags?.length ? [...row.tags] : []
  tagDialogVisible.value = true
}

async function saveTags() {
  if (!editingDoc.value) return

  const tags = normalizeTags(tagEditTags.value)
  const validationError = validateTags(tags)
  if (validationError) {
    ElMessage.warning(validationError)
    return
  }

  tagSaving.value = true
  try {
    await updateDocumentTags(editingDoc.value.id, tags)
    ElMessage.success('标签已更新，检索时将按新标签过滤')
    tagDialogVisible.value = false
    await loadDocuments()
  } catch (error: any) {
    ElMessage.error(error?.message ?? '标签保存失败')
  } finally {
    tagSaving.value = false
  }
}

function closeTagDialog() {
  if (tagSaving.value) return
  tagDialogVisible.value = false
  editingDoc.value = null
  tagEditTags.value = []
}

async function openChunkDialog(row: DocumentItem) {
  chunkDialogVisible.value = true
  chunkLoading.value = true
  chunks.value = []
  try {
    const data = await fetchDocumentChunks(row.id)
    chunks.value = data.records
  } catch (error: any) {
    ElMessage.error(error?.message ?? '加载切片失败')
  } finally {
    chunkLoading.value = false
  }
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function goRecycleBin() {
  router.push(`/bases/${kbId.value}/recycle-bin`)
}

/** US3.1：上传成功后刷新列表，默认展示「已上传」状态 */
function onUploadSuccess() {
  statusFilter.value = 'UPLOADED'
  page.value = 1
  loadDocuments()
}

watch([page, pageSize, statusFilter], () => loadDocuments())

onMounted(loadDocuments)
</script>

<template>
  <div class="document-list-page">
    <div class="page-header">
      <div>
        <h2>文档管理</h2>
        <p class="subtitle">知识库 ID：{{ kbId }}</p>
      </div>
      <div class="actions">
        <el-button @click="goRecycleBin">回收站</el-button>
        <el-button type="primary" @click="uploadVisible = true">上传文档</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <div class="toolbar">
        <el-select
          v-model="statusFilter"
          placeholder="状态筛选"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索文件名"
          clearable
          style="width: 220px"
          @keyup.enter="loadDocuments"
        />
        <el-button @click="loadDocuments">查询</el-button>
        <el-button
          type="danger"
          plain
          :disabled="!selectedIds.length || batchDeleting"
          :loading="batchDeleting"
          @click="handleBatchDelete"
        >
          批量删除{{ selectedIds.length ? ` (${selectedIds.length})` : '' }}
        </el-button>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="documents"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="标签" min-width="160">
          <template #default="{ row }">
            <el-tag
              v-for="tag in row.tags"
              :key="tag"
              size="small"
              type="info"
              style="margin-right: 4px; margin-bottom: 4px"
            >
              {{ tag }}
            </el-tag>
            <span v-if="!row.tags?.length" class="muted">暂无标签</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <DocumentStatusTag :status="row.status" />
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize || 0) }}</template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="80" />
        <el-table-column prop="createdAt" label="上传时间" width="170" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTagDialog(row)">编辑标签</el-button>
            <el-button
              v-if="row.status === 'READY'"
              link
              type="primary"
              @click="openChunkDialog(row)"
            >
              查看切片
            </el-button>
            <el-button
              link
              type="danger"
              :loading="deletingDocId === row.id"
              :disabled="deletingDocId !== null && deletingDocId !== row.id"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </el-card>

    <UploadDocumentDialog
      :visible="uploadVisible"
      :kb-id="kbId"
      @update:visible="uploadVisible = $event"
      @success="onUploadSuccess"
    />

    <el-dialog
      v-model="tagDialogVisible"
      title="编辑文档标签"
      width="520px"
      destroy-on-close
      @close="closeTagDialog"
    >
      <div v-if="editingDoc" class="tag-dialog-body">
        <p class="tag-doc-name">
          文档：<strong>{{ editingDoc.originalName }}</strong>
        </p>
        <p class="tag-hint">修改后保存，知识检索（US5.6）将按新标签进行过滤。</p>
        <el-form label-width="72px">
          <el-form-item label="标签">
            <el-select
              v-model="tagEditTags"
              multiple
              filterable
              allow-create
              default-first-option
              :reserve-keyword="false"
              placeholder="输入标签后回车，或从列表选择"
              style="width: 100%"
              :disabled="tagSaving"
            >
              <el-option
                v-for="tag in tagEditTags"
                :key="tag"
                :label="tag"
                :value="tag"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <div v-if="tagEditTags.length" class="tag-preview">
          <span class="preview-label">预览：</span>
          <el-tag
            v-for="tag in normalizeTags(tagEditTags)"
            :key="tag"
            size="small"
            style="margin: 2px"
          >
            {{ tag }}
          </el-tag>
        </div>
      </div>
      <template #footer>
        <el-button :disabled="tagSaving" @click="closeTagDialog">取消</el-button>
        <el-button type="primary" :loading="tagSaving" @click="saveTags">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="chunkDialogVisible" title="切片详情" width="720px">
      <el-skeleton v-if="chunkLoading" :rows="5" animated />
      <div v-else class="chunk-list">
        <div v-for="chunk in chunks" :key="chunk.chunkIndex" class="chunk-item">
          <div class="chunk-head">
            <strong>#{{ chunk.chunkIndex }}</strong>
            <span>{{ chunk.charCount }} 字符</span>
          </div>
          <pre class="chunk-content">{{ chunk.content }}</pre>
        </div>
        <el-empty v-if="!chunks.length" description="暂无切片" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.document-list-page {
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

.subtitle {
  margin: 4px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.actions {
  display: flex;
  gap: 8px;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.muted {
  color: var(--el-text-color-placeholder);
}

.tag-dialog-body {
  padding-top: 4px;
}

.tag-doc-name {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.tag-hint {
  margin: 0 0 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tag-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.preview-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chunk-list {
  max-height: 480px;
  overflow-y: auto;
}

.chunk-item {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}

.chunk-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.chunk-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  font-size: 13px;
}
</style>
