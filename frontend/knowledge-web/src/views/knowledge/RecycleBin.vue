<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchRecycleBin,
  permanentDeleteDocument,
  restoreDocument,
} from '@/api/modules/document'
import type { DocumentItem } from '@/types/knowledge'

const route = useRoute()
const router = useRouter()

const kbId = ref<number>(Number(route.params.kbId))
const loading = ref(false)
const documents = ref<DocumentItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

async function loadRecycleBin() {
  loading.value = true
  try {
    const data = await fetchRecycleBin(kbId.value, page.value, pageSize.value)
    documents.value = data.records
    total.value = data.total
  } catch (error: any) {
    ElMessage.error(error?.message ?? '加载失败')
  } finally {
    loading.value = false
  }
}

function calcRemainingDays(row: DocumentItem) {
  if (!row.deletedAt) return '-'
  const diff = new Date(row.deletedAt).getTime() - Date.now()
  return `${Math.max(Math.ceil(diff / (1000 * 60 * 60 * 24)), 0)} 天`
}

function formatSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

async function handleRestore(row: DocumentItem) {
  try {
    await restoreDocument(row.id)
    ElMessage.success('文档已恢复')
    loadRecycleBin()
  } catch (error: any) {
    ElMessage.error(error?.message ?? '恢复失败')
  }
}

async function handlePermanentDelete(row: DocumentItem) {
  try {
    await ElMessageBox.confirm(
      `确定永久删除「${row.originalName}」？此操作不可恢复，将由 Worker 异步清理 MinIO 和向量。`,
      '永久删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await permanentDeleteDocument(row.id)
    ElMessage.success('已发起永久删除，Worker 处理中')
    loadRecycleBin()
  } catch (error: any) {
    ElMessage.error(error?.message ?? '永久删除失败')
  }
}

function goBack() {
  router.push(`/bases/${kbId.value}/documents`)
}

watch([page, pageSize], () => loadRecycleBin())

onMounted(loadRecycleBin)
</script>

<template>
  <div class="recycle-bin-page">
    <div class="page-header">
      <div>
        <h2>回收站</h2>
        <p class="subtitle">文档删除后保留 30 天，到期后由 Worker 自动清理</p>
      </div>
      <el-button @click="goBack">返回文档列表</el-button>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="documents" row-key="id">
        <el-table-column prop="originalName" label="文件名" min-width="200" show-overflow-tooltip />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize || 0) }}</template>
        </el-table-column>
        <el-table-column prop="deletedAt" label="删除时间" width="170" />
        <el-table-column label="剩余保留" width="110">
          <template #default="{ row }">{{ calcRemainingDays(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleRestore(row)">恢复</el-button>
            <el-button link type="danger" @click="handlePermanentDelete(row)">永久删除</el-button>
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
  </div>
</template>

<style scoped>
.recycle-bin-page {
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

.pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
