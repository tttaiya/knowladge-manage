<template>
  <div class="review-page">
    <section class="toolbar">
      <div>
        <h2>审核工作台</h2>
        <p>待审核文档、分块维护与审核流转</p>
      </div>
      <div class="toolbar-actions">
        <el-input
          v-model="kbIdFilter"
          clearable
          placeholder="知识库 ID"
          class="kb-filter"
          @clear="reloadList"
          @keyup.enter="reloadList"
        />
        <el-button :loading="listLoading" @click="reloadList">刷新列表</el-button>
      </div>
    </section>

    <section class="workbench">
      <aside class="document-list">
        <div class="panel-title">
          <span>待审核文档</span>
          <el-tag size="small" type="warning">{{ total }} 条</el-tag>
        </div>
        <el-skeleton v-if="listLoading && !documents.length" :rows="6" animated />
        <el-empty v-else-if="!documents.length" description="暂无待审核文档" />
        <template v-else>
          <button
            v-for="doc in documents"
            :key="doc.docId"
            class="doc-item"
            :class="{ active: selectedDocId === doc.docId }"
            @click="selectDocument(doc.docId)"
          >
            <span class="doc-name">{{ doc.originalName }}</span>
            <span class="doc-meta">{{ doc.kbName || `知识库 ${doc.kbId}` }}</span>
            <span class="doc-foot">
              <el-tag size="small" type="primary">{{ statusLabel(doc.status) }}</el-tag>
              <span>{{ doc.chunkCount || 0 }} 块</span>
            </span>
          </button>
        </template>
        <el-pagination
          v-if="total > pageSize"
          small
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="changePage"
        />
      </aside>

      <main class="detail-panel">
        <el-empty v-if="!selectedDocId" description="请选择一个待审核文档" />
        <el-skeleton v-else-if="detailLoading && !detail" :rows="8" animated />
        <template v-else-if="detail">
          <div class="detail-header">
            <div>
              <h3>{{ detail.originalName }}</h3>
              <div class="detail-subtitle">
                <span>{{ detail.kbName || `知识库 ${detail.kbId}` }}</span>
                <span>文档 ID: {{ detail.docId }}</span>
                <span>分块: {{ detail.chunkCount || detail.chunks.length }}</span>
              </div>
            </div>
            <div class="detail-actions">
              <el-tag :type="detail.status === 'PENDING_REVIEW' ? 'primary' : 'info'">
                {{ statusLabel(detail.status) }}
              </el-tag>
              <el-button :loading="detailLoading" @click="refreshDetail">刷新状态</el-button>
            </div>
          </div>

          <div v-if="detail.tags?.length" class="tag-row">
            <el-tag v-for="tag in detail.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag>
          </div>

          <section class="action-bar">
            <el-input
              v-model="approveComment"
              placeholder="通过备注，可选"
              :disabled="reviewSubmitting || detail.status !== 'PENDING_REVIEW'"
            />
            <el-button
              type="success"
              :loading="reviewSubmitting"
              :disabled="detail.status !== 'PENDING_REVIEW' || hasUnreadyChunks"
              @click="approve"
            >
              审核通过
            </el-button>
          </section>

          <section class="reject-box">
            <el-input
              v-model="rejectReason"
              type="textarea"
              :rows="2"
              placeholder="拒绝原因，必填"
              :disabled="reviewSubmitting || detail.status !== 'PENDING_REVIEW'"
            />
            <el-button
              type="danger"
              :loading="reviewSubmitting"
              :disabled="detail.status !== 'PENDING_REVIEW'"
              @click="reject"
            >
              审核拒绝
            </el-button>
          </section>

          <el-alert
            v-if="hasUnreadyChunks"
            type="warning"
            :closable="false"
            show-icon
            title="存在未完成向量化的分块，暂不能审核通过"
          />

          <el-table :data="detail.chunks" border class="chunk-table" row-key="chunkId">
            <el-table-column prop="chunkIndex" label="序号" width="72" />
            <el-table-column prop="chapterPath" label="标题路径" min-width="140" show-overflow-tooltip />
            <el-table-column prop="pageNo" label="页码" width="72" />
            <el-table-column prop="chunkType" label="类型" width="90" />
            <el-table-column prop="charCount" label="字符数" width="88" />
            <el-table-column label="向量状态" width="110">
              <template #default="{ row }">
                <el-tag :type="row.vectorStatus === 'READY' ? 'success' : 'warning'" size="small">
                  {{ row.vectorStatus || 'UNKNOWN' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="已编辑" width="78">
              <template #default="{ row }">
                <el-tag v-if="row.isEdited" type="warning" size="small">是</el-tag>
                <span v-else>否</span>
              </template>
            </el-table-column>
            <el-table-column label="正文" min-width="280">
              <template #default="{ row }">
                <pre class="chunk-content">{{ row.content }}</pre>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="92" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  :disabled="detail.status !== 'PENDING_REVIEW'"
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </main>
    </section>

    <review-chunk-editor
      v-model="editVisible"
      :chunk="editChunk"
      :submitting="editSubmitting"
      @save="submitChunkEdit"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ReviewChunkEditor from '@/components/knowledge/ReviewChunkEditor.vue'
import {
  approveReviewDocument,
  fetchPendingReviewDocuments,
  fetchReviewDocumentDetail,
  rejectReviewDocument,
  updateReviewChunk,
  type PendingReviewDocument,
  type ReviewChunk,
  type ReviewDocumentDetail,
} from '@/api/modules/review'

const documents = ref<PendingReviewDocument[]>([])
const detail = ref<ReviewDocumentDetail | null>(null)
const selectedDocId = ref<number | null>(null)
const listLoading = ref(false)
const detailLoading = ref(false)
const reviewSubmitting = ref(false)
const editSubmitting = ref(false)
const editVisible = ref(false)
const editChunk = ref<ReviewChunk | null>(null)
const rejectReason = ref('')
const approveComment = ref('')
const kbIdFilter = ref('')
const page = ref(1)
const pageSize = 10
const total = ref(0)

const hasUnreadyChunks = computed(() =>
  Boolean(detail.value?.chunks.some((chunk) => (chunk.vectorStatus || '').toUpperCase() !== 'READY')),
)

onMounted(() => {
  reloadList()
})

async function reloadList() {
  listLoading.value = true
  try {
    const data = await fetchPendingReviewDocuments({
      kbId: parsedKbId(),
      page: page.value,
      pageSize,
    })
    documents.value = data.records || []
    total.value = data.total || 0
    if (!selectedDocId.value && documents.value.length) {
      await selectDocument(documents.value[0].docId)
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载待审核列表失败'))
  } finally {
    listLoading.value = false
  }
}

async function changePage(nextPage: number) {
  page.value = nextPage
  await reloadList()
}

async function selectDocument(docId: number) {
  selectedDocId.value = docId
  rejectReason.value = ''
  approveComment.value = ''
  await refreshDetail()
}

async function refreshDetail() {
  if (!selectedDocId.value) return
  detailLoading.value = true
  try {
    detail.value = await fetchReviewDocumentDetail(selectedDocId.value)
  } catch (error) {
    ElMessage.error(errorMessage(error, '加载文档详情失败'))
  } finally {
    detailLoading.value = false
  }
}

async function approve() {
  if (!detail.value) return
  if (hasUnreadyChunks.value) {
    ElMessage.warning('仍有分块未完成向量化，不能审核通过')
    return
  }
  try {
    await ElMessageBox.confirm('确认将该文档审核通过并进入 READY 状态？', '审核通过', { type: 'warning' })
  } catch {
    return
  }
  reviewSubmitting.value = true
  try {
    await approveReviewDocument(detail.value.docId, approveComment.value.trim() || undefined)
    ElMessage.success('审核通过成功')
    await afterReviewChanged()
  } catch (error) {
    ElMessage.error(errorMessage(error, '审核通过失败'))
  } finally {
    reviewSubmitting.value = false
  }
}

async function reject() {
  if (!detail.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  try {
    await ElMessageBox.confirm('确认拒绝该文档？系统将记录原因并触发后续处理。', '审核拒绝', { type: 'warning' })
  } catch {
    return
  }
  reviewSubmitting.value = true
  try {
    await rejectReviewDocument(detail.value.docId, reason)
    ElMessage.success('审核拒绝成功')
    await afterReviewChanged()
  } catch (error) {
    ElMessage.error(errorMessage(error, '审核拒绝失败'))
  } finally {
    reviewSubmitting.value = false
  }
}

function openEdit(row: ReviewChunk) {
  editChunk.value = row
  editVisible.value = true
}

async function submitChunkEdit(content: string) {
  if (!editChunk.value) return
  const nextContent = content.trim()
  if (!nextContent) {
    ElMessage.warning('分块正文不能为空')
    return
  }
  editSubmitting.value = true
  try {
    await updateReviewChunk(editChunk.value.chunkId, nextContent)
    ElMessage.success('分块已保存，已创建重向量化任务')
    editVisible.value = false
    await refreshDetail()
  } catch (error) {
    ElMessage.error(errorMessage(error, '保存分块失败'))
  } finally {
    editSubmitting.value = false
  }
}

async function afterReviewChanged() {
  selectedDocId.value = null
  detail.value = null
  await reloadList()
}

function statusLabel(status: string) {
  const map: Record<string, string> = {
    PENDING_REVIEW: '待审核',
    READY: '就绪',
    REVIEW_REJECTED: '审核未通过',
    VECTORIZING: '向量化中',
    FAILED: '失败',
  }
  return map[status] || status
}

function parsedKbId() {
  const value = Number(kbIdFilter.value)
  return Number.isFinite(value) && value > 0 ? value : undefined
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback
}
</script>

<style scoped>
.review-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: calc(100vh - 96px);
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar h2,
.detail-header h3 {
  margin: 0;
}

.toolbar p,
.detail-subtitle {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
}

.toolbar-actions,
.detail-actions,
.action-bar,
.reject-box,
.tag-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kb-filter {
  width: 150px;
}

.workbench {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  min-height: 0;
}

.document-list,
.detail-panel {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 14px;
  min-height: 560px;
}

.document-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.panel-title,
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.doc-item {
  display: flex;
  flex-direction: column;
  gap: 7px;
  width: 100%;
  padding: 12px;
  text-align: left;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
}

.doc-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.doc-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.doc-meta,
.doc-foot {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.doc-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
}

.detail-subtitle {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.action-bar .el-input,
.reject-box .el-textarea {
  flex: 1;
}

.chunk-table {
  width: 100%;
}

.chunk-content {
  max-height: 110px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  color: var(--el-text-color-regular);
}

@media (max-width: 980px) {
  .toolbar,
  .workbench {
    display: flex;
    flex-direction: column;
    align-items: stretch;
  }

  .document-list,
  .detail-panel {
    min-height: auto;
  }
}
</style>
