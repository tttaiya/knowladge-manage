<template>
  <div class="page">
    <header class="toolbar">
      <h2>知识库管理</h2>
      <div class="filters">
        <input v-model.trim="query.q" placeholder="请输入知识库名称" @keyup.enter="loadData" />
        <select v-model="query.category" @change="loadData">
          <option value="">全部</option>
          <option value="REGULATION">政策法规</option>
          <option value="REPORT_PAPER">研报论文</option>
          <option value="TERM">术语词库</option>
          <option value="GENERAL">通用知识</option>
        </select>
        <button @click="showCreate = true">新建知识库</button>
        <button :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</button>
      </div>
    </header>

    <table>
      <thead>
        <tr>
          <th><input type="checkbox" :checked="allChecked" @change="toggleAll($event)" /></th>
          <th>名称</th>
          <th>描述</th>
          <th>分类</th>
          <th>文档数</th>
          <th>创建时间</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in pageData.records" :key="item.id">
          <td><input type="checkbox" :value="item.id" v-model="selectedIds" /></td>
          <td>{{ item.name }}</td>
          <td>{{ item.description || '-' }}</td>
          <td>{{ categoryLabel(item.category) }}</td>
          <td>{{ item.documentCount }}</td>
          <td>{{ item.createdAt || '-' }}</td>
          <td class="actions">
            <button @click="goDetail(item.id)">详情</button>
            <button @click="openEdit(item)">编辑</button>
            <button @click="handleDelete(item.id)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>

    <footer class="pager">
      <button :disabled="query.page <= 1" @click="prevPage">上一页</button>
      <span>第 {{ query.page }} 页 / 共 {{ totalPages }} 页</span>
      <button :disabled="query.page >= totalPages" @click="nextPage">下一页</button>
    </footer>

    <KnowledgeBaseFormDialog v-model:visible="showCreate" @success="reload" />
    <KnowledgeBaseFormDialog v-model:visible="showEdit" :knowledge-base="editingItem" @success="reload" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { batchDeleteKnowledgeBases, deleteKnowledgeBase, listKnowledgeBases } from '../../api/modules/knowledge-base'
import KnowledgeBaseFormDialog from '../../components/knowledge/KnowledgeBaseFormDialog.vue'
import type { KnowledgeBaseQuery, KnowledgeBaseVO, PageResult } from '../../types/knowledge'

const router = useRouter()
const showCreate = ref(false)
const showEdit = ref(false)
const editingItem = ref<KnowledgeBaseVO | null>(null)
const selectedIds = ref<number[]>([])
const query = reactive<KnowledgeBaseQuery>({
  q: '',
  category: '',
  page: 1,
  pageSize: 10
})
const pageData = ref<PageResult<KnowledgeBaseVO>>({
  records: [],
  total: 0,
  page: 1,
  pageSize: 10
})

const totalPages = computed(() => Math.max(1, Math.ceil(pageData.value.total / query.pageSize)))
const allChecked = computed(() => pageData.value.records.length > 0 && pageData.value.records.every(item => selectedIds.value.includes(item.id)))

function categoryLabel(category: string) {
  const mapping: Record<string, string> = {
    REGULATION: '政策法规',
    REPORT_PAPER: '研报论文',
    TERM: '术语词库',
    GENERAL: '通用知识'
  }
  return mapping[category] || category
}

async function loadData() {
  try {
    pageData.value = await listKnowledgeBases(query)
    selectedIds.value = []
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '查询知识库列表失败')
  }
}

function reload() {
  void loadData()
}

function openEdit(item: KnowledgeBaseVO) {
  editingItem.value = item
  showEdit.value = true
}

function goDetail(id: number) {
  void router.push('/knowledge-management/bases/' + id)
}

async function handleDelete(id: number) {
  if (!window.confirm('确认删除当前知识库吗？')) return
  try {
    await deleteKnowledgeBase(id)
    await loadData()
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '删除知识库失败')
  }
}

async function handleBatchDelete() {
  if (!window.confirm('确认批量删除已选中的知识库吗？')) return
  try {
    await batchDeleteKnowledgeBases(selectedIds.value)
    await loadData()
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '批量删除知识库失败')
  }
}

function toggleAll(event: Event) {
  const checked = (event.target as HTMLInputElement).checked
  selectedIds.value = checked ? pageData.value.records.map(item => item.id) : []
}

function prevPage() {
  if (query.page > 1) {
    query.page -= 1
    void loadData()
  }
}

function nextPage() {
  if (query.page < totalPages.value) {
    query.page += 1
    void loadData()
  }
}

onMounted(() => {
  void loadData()
})
</script>

<style scoped>
.page {
  padding: 24px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.filters {
  display: flex;
  gap: 12px;
}
input,
select,
button {
  padding: 8px 12px;
}
table {
  width: 100%;
  border-collapse: collapse;
  background: #fff;
}
th,
td {
  border: 1px solid #e5e7eb;
  padding: 12px;
  text-align: left;
}
.actions {
  display: flex;
  gap: 8px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: center;
  gap: 12px;
  align-items: center;
}
</style>