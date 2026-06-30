<template>
  <div class="kb-list-page">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="openCreate">+ 新建知识库</el-button>
    </div>

    <el-card class="filter-card">
      <el-form :inline="true" :model="filter">
        <el-form-item label="分类">
          <el-select v-model="filter.category" placeholder="全部分类" clearable style="width: 160px">
            <el-option v-for="c in KB_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="filter.nameKeyword" placeholder="模糊匹配" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.isDeleted" placeholder="全部" clearable style="width: 120px">
            <el-option label="活动" :value="0" />
            <el-option label="已删除" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="reload">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="name" label="名称" min-width="200" />
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
        </el-table-column>
        <el-table-column prop="retrievalStrategy" label="检索策略" width="120">
          <template #default="{ row }">{{ strategyLabel(row.retrievalStrategy) }}</template>
        </el-table-column>
        <el-table-column prop="chunkStrategy" label="切片策略" width="120">
          <template #default="{ row }">{{ chunkStrategyLabel(row.chunkStrategy) }}</template>
        </el-table-column>
        <el-table-column prop="documentCount" label="文档数" width="80" />
        <el-table-column prop="strategyVersion" label="策略版本" width="100" />
        <el-table-column prop="createdByName" label="创建人" width="120" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row._isDeleted" link type="primary" @click="goDetail(row)">详情</el-button>
            <el-button v-if="!row._isDeleted" link type="primary" @click="goDocuments(row)">文档</el-button>
            <el-button v-if="!row._isDeleted" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="!row._isDeleted" link type="primary" @click="confirmReprocess(row)">策略变更</el-button>
            <el-button v-if="!row._isDeleted" link type="danger" @click="confirmDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="reload"
        @size-change="reload"
        class="pagination"
      />
    </el-card>

    <KnowledgeBaseFormDialog
      v-model:visible="dialogVisible"
      :kb="editing"
      :mode="dialogMode"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchDeleteKnowledgeBases,
  deleteKnowledgeBase,
  listKnowledgeBases,
  reprocessKnowledgeBase,
  type KnowledgeBaseVO,
} from '@/api/modules/knowledge-base'
import {
  KB_CATEGORIES,
  KB_CHUNK_STRATEGIES,
  KB_RETRIEVAL_STRATEGIES,
} from '@/types/knowledge-base'
import KnowledgeBaseFormDialog from '@/components/knowledge/KnowledgeBaseFormDialog.vue'

const router = useRouter()

const filter = reactive<{ category?: string; nameKeyword?: string; isDeleted?: number }>({})
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const rows = ref<KnowledgeBaseVO[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editing = ref<KnowledgeBaseVO | null>(null)

async function reload() {
  loading.value = true
  try {
    const resp = await listKnowledgeBases({
      ...filter,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    if (resp.code !== 0) {
      ElMessage.error(resp.message || '查询失败')
      rows.value = []
      total.value = 0
      return
    }
    rows.value = (resp.data.list || []).map((r) => ({ ...r, _isDeleted: r._isDeleted }))
    total.value = resp.data.total
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function resetFilter() {
  filter.category = undefined
  filter.nameKeyword = undefined
  filter.isDeleted = undefined
  pageNum.value = 1
  reload()
}

function openCreate() {
  dialogMode.value = 'create'
  editing.value = null
  dialogVisible.value = true
}

function openEdit(row: KnowledgeBaseVO) {
  dialogMode.value = 'edit'
  editing.value = row
  dialogVisible.value = true
}

function onSaved() {
  dialogVisible.value = false
  reload()
}

function goDetail(row: KnowledgeBaseVO) {
  router.push({ name: 'KnowledgeBaseDetail', params: { kbId: String(row.id) } })
}

function goDocuments(row: KnowledgeBaseVO) {
  router.push({ name: 'DocumentList', params: { kbId: String(row.id) } })
}

async function confirmDelete(row: KnowledgeBaseVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除知识库「${row.name}」？关联文档将一并放入回收站。`,
      '确认删除',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const resp = await deleteKnowledgeBase(row.id)
  if (resp.code === 0) {
    ElMessage.success('已删除')
    reload()
  } else {
    ElMessage.error(resp.message || '删除失败')
  }
}

async function batchDelete() {
  // 列表内未实现多选；保留入口给后续增强
}

async function confirmReprocess(row: KnowledgeBaseVO) {
  try {
    await ElMessageBox.confirm(
      `将触发对「${row.name}」下所有文档的策略变更（切片+向量化重做）。确认？`,
      '策略变更',
      { type: 'warning' },
    )
  } catch {
    return
  }
  const resp = await reprocessKnowledgeBase(row.id)
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

onMounted(() => reload())
</script>

<style scoped>
.kb-list-page {
  padding: 16px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
}
.filter-card,
.table-card {
  margin-bottom: 16px;
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
