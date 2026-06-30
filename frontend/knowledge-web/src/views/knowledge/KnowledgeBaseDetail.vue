<template>
  <div class="kb-detail-page">
    <div class="page-header">
      <el-button @click="goBack">← 返回</el-button>
      <h2 v-if="detail">知识库详情：{{ detail.name }}</h2>
    </div>

    <el-card v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="名称">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryLabel(detail.category) }}</el-descriptions-item>
          <el-descriptions-item label="检索策略">{{ strategyLabel(detail.retrievalStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="切片策略">{{ chunkStrategyLabel(detail.chunkStrategy) }}</el-descriptions-item>
          <el-descriptions-item label="切片参数">{{ detail.chunkSize }} / {{ detail.chunkOverlap }}</el-descriptions-item>
          <el-descriptions-item label="文档数">{{ detail.documentCount }}</el-descriptions-item>
          <el-descriptions-item label="策略版本">v{{ detail.strategyVersion }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.createdByName || 'anonymous' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="最后更新">{{ detail.updatedAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">
            {{ detail.description || '（无）' }}
          </el-descriptions-item>
          <el-descriptions-item label="分隔符" :span="2">
            <el-tag
              v-for="s in detail.separators"
              :key="s"
              type="info"
              style="margin-right: 6px; margin-bottom: 4px"
            >
              {{ JSON.stringify(s) }}
            </el-tag>
            <span v-if="!detail.separators?.length" class="muted">（默认）</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button @click="goDocuments">查看文档</el-button>
          <el-button type="primary" @click="openEdit">编辑</el-button>
          <el-button type="warning" @click="confirmReprocess">策略变更</el-button>
          <el-button type="danger" @click="confirmDelete">删除</el-button>
        </div>
      </template>
    </el-card>

    <KnowledgeBaseFormDialog
      v-model:visible="dialogVisible"
      :kb="detail"
      mode="edit"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteKnowledgeBase,
  getKnowledgeBaseDetail,
  reprocessKnowledgeBase,
  type KnowledgeBaseDetailVO,
} from '@/api/modules/knowledge-base'
import {
  KB_CATEGORIES,
  KB_CHUNK_STRATEGIES,
  KB_RETRIEVAL_STRATEGIES,
} from '@/types/knowledge-base'
import KnowledgeBaseFormDialog from '@/components/knowledge/KnowledgeBaseFormDialog.vue'

const route = useRoute()
const router = useRouter()

const kbId = ref<number>(Number(route.params.kbId))
const detail = ref<KnowledgeBaseDetailVO | null>(null)
const loading = ref(false)
const dialogVisible = ref(false)

async function fetchDetail() {
  loading.value = true
  try {
    const resp = await getKnowledgeBaseDetail(kbId.value)
    if (resp.code !== 0) {
      ElMessage.error(resp.message || '加载失败')
      detail.value = null
      return
    }
    detail.value = resp.data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ name: 'KnowledgeBaseList' })
}

function goDocuments() {
  router.push({ name: 'DocumentList', params: { kbId: String(kbId.value) } })
}

function openEdit() {
  dialogVisible.value = true
}

function onSaved() {
  dialogVisible.value = false
  fetchDetail()
}

async function confirmDelete() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${detail.value.name}」？关联文档将一并放入回收站。`,
      '确认删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const resp = await deleteKnowledgeBase(kbId.value)
  if (resp.code === 0) {
    ElMessage.success('已删除')
    goBack()
  } else {
    ElMessage.error(resp.message || '删除失败')
  }
}

async function confirmReprocess() {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(
      `将触发对「${detail.value.name}」下所有文档的策略变更（切片+向量化重做）。确认？`,
      '策略变更',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const resp = await reprocessKnowledgeBase(kbId.value)
  if (resp.code === 0) {
    ElMessage.success(resp.data.message || '已触发策略变更')
  } else {
    ElMessage.error(resp.message || '触发失败')
  }
}

function categoryLabel(v: string) {
  return KB_CATEGORIES.find((c) => c.value === v)?.label || v
}
function strategyLabel(v: string) {
  return KB_RETRIEVAL_STRATEGIES.find((s) => s.value === v)?.label || v
}
function chunkStrategyLabel(v: string) {
  return KB_CHUNK_STRATEGIES.find((s) => s.value === v)?.label || v
}

watch(
  () => route.params.kbId,
  (v) => {
    if (v) {
      kbId.value = Number(v)
      fetchDetail()
    }
  },
)

onMounted(() => fetchDetail())
</script>

<style scoped>
.kb-detail-page {
  padding: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.actions {
  margin-top: 24px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.muted {
  color: var(--el-text-color-secondary);
}
</style>
