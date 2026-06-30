<template>
  <div class="page" v-if="detail">
    <header class="header">
      <h2>{{ detail.name }}</h2>
      <div class="actions">
        <button @click="showEdit = true">编辑</button>
        <button @click="handleReprocess">重新切片</button>
        <button @click="goBack">返回列表</button>
      </div>
    </header>

    <section class="card">
      <p><strong>描述：</strong>{{ detail.description || '-' }}</p>
      <p><strong>分类：</strong>{{ categoryLabel(detail.category) }}</p>
      <p><strong>检索策略：</strong>{{ retrievalStrategyLabel(detail.retrievalStrategy) }}</p>
      <p><strong>切片策略：</strong>{{ chunkStrategyLabel(detail.chunkStrategy) }}</p>
      <p><strong>切片大小：</strong>{{ detail.chunkSize }}</p>
      <p><strong>切片重叠：</strong>{{ detail.chunkOverlap }}</p>
      <p><strong>文档数量：</strong>{{ detail.documentCount }}</p>
      <p><strong>创建时间：</strong>{{ detail.createdAt || '-' }}</p>
      <p><strong>更新时间：</strong>{{ detail.updatedAt || '-' }}</p>
    </section>

    <KnowledgeBaseFormDialog v-model:visible="showEdit" :knowledge-base="detail" @success="loadDetail" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeBase, reprocessKnowledgeBase } from '../../api/modules/knowledge-base'
import KnowledgeBaseFormDialog from '../../components/knowledge/KnowledgeBaseFormDialog.vue'
import type { KnowledgeBaseVO } from '../../types/knowledge'

const route = useRoute()
const router = useRouter()
const detail = ref<KnowledgeBaseVO | null>(null)
const showEdit = ref(false)

function categoryLabel(category: string) {
  const mapping: Record<string, string> = {
    REGULATION: '政策法规',
    REPORT_PAPER: '研报论文',
    TERM: '术语词库',
    GENERAL: '通用知识'
  }
  return mapping[category] || category
}

function retrievalStrategyLabel(strategy: string) {
  const mapping: Record<string, string> = {
    SEMANTIC: '语义检索',
    VECTOR_RERANK: '向量检索 + 重排'
  }
  return mapping[strategy] || strategy
}

function chunkStrategyLabel(strategy: string) {
  const mapping: Record<string, string> = {
    HEADING: '按标题切片',
    FIXED: '固定长度切片'
  }
  return mapping[strategy] || strategy
}

async function loadDetail() {
  try {
    detail.value = await getKnowledgeBase(Number(route.params.id))
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '查询知识库详情失败')
  }
}

async function handleReprocess() {
  if (!detail.value) return
  if (!window.confirm('确认对该知识库下 READY 文档重新切片吗？')) return
  try {
    const result = await reprocessKnowledgeBase(detail.value.id)
    window.alert(result.message)
    await loadDetail()
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '重新切片失败')
  }
}

function goBack() {
  void router.push('/knowledge-management/bases')
}

onMounted(() => {
  void loadDetail()
})
</script>

<style scoped>
.page { padding: 24px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.actions { display: flex; gap: 8px; }
.card { margin-top: 16px; padding: 20px; background: #fff; border: 1px solid #ddd; border-radius: 8px; }
</style>