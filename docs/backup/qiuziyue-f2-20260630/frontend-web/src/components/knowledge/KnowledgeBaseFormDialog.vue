<template>
  <div v-if="visible" class="overlay">
    <div class="dialog">
      <h3>{{ isEdit ? '编辑知识库' : '新建知识库' }}</h3>
      <form @submit.prevent="handleSubmit">
        <label>
          名称
          <input v-model.trim="form.name" required />
        </label>
        <label>
          描述
          <textarea v-model="form.description" rows="3" />
        </label>
        <label>
          分类
          <select v-model="form.category">
            <option value="REGULATION">政策法规</option>
            <option value="REPORT_PAPER">研报论文</option>
            <option value="TERM">术语词库</option>
            <option value="GENERAL">通用知识</option>
          </select>
        </label>
        <label>
          检索策略
          <select v-model="form.retrievalStrategy">
            <option value="SEMANTIC">语义检索</option>
            <option value="VECTOR_RERANK">向量检索 + 重排</option>
          </select>
        </label>
        <label>
          切片策略
          <select v-model="form.chunkStrategy">
            <option value="HEADING">按标题切片</option>
            <option value="FIXED">固定长度切片</option>
          </select>
        </label>
        <label>
          切片大小
          <input v-model.number="form.chunkSize" type="number" min="1" />
        </label>
        <label>
          切片重叠
          <input v-model.number="form.chunkOverlap" type="number" min="0" />
        </label>
        <label>
          分隔符配置
          <textarea v-model="form.separatorsJson" rows="2" placeholder='["\n\n","\n","。"]' />
        </label>
        <div class="actions">
          <button type="button" @click="close">取消</button>
          <button type="submit">保存</button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { createKnowledgeBase, updateKnowledgeBase } from '../../api/modules/knowledge-base'
import type { KnowledgeBaseCreateDTO, KnowledgeBaseVO } from '../../types/knowledge'

const props = defineProps<{
  visible: boolean
  knowledgeBase?: KnowledgeBaseVO | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const isEdit = computed(() => !!props.knowledgeBase)
const form = reactive<KnowledgeBaseCreateDTO>({
  name: '',
  description: '',
  category: 'GENERAL',
  retrievalStrategy: 'SEMANTIC',
  chunkStrategy: 'HEADING',
  chunkSize: 500,
  chunkOverlap: 50,
  separatorsJson: ''
})

function resetForm() {
  form.name = ''
  form.description = ''
  form.category = 'GENERAL'
  form.retrievalStrategy = 'SEMANTIC'
  form.chunkStrategy = 'HEADING'
  form.chunkSize = 500
  form.chunkOverlap = 50
  form.separatorsJson = ''
}

watch(
  () => props.visible,
  (value) => {
    if (!value) return
    if (props.knowledgeBase) {
      form.name = props.knowledgeBase.name
      form.description = props.knowledgeBase.description || ''
      form.category = props.knowledgeBase.category
      form.retrievalStrategy = props.knowledgeBase.retrievalStrategy
      form.chunkStrategy = props.knowledgeBase.chunkStrategy
      form.chunkSize = props.knowledgeBase.chunkSize
      form.chunkOverlap = props.knowledgeBase.chunkOverlap
      form.separatorsJson = props.knowledgeBase.separatorsJson || ''
    } else {
      resetForm()
    }
  },
  { immediate: true }
)

function close() {
  emit('update:visible', false)
}

async function handleSubmit() {
  if (form.chunkStrategy === 'FIXED' && form.chunkOverlap >= form.chunkSize) {
    window.alert('FIXED 策略下，chunkOverlap 必须小于 chunkSize')
    return
  }
  try {
    if (props.knowledgeBase) {
      const strategyChanged = props.knowledgeBase.retrievalStrategy !== form.retrievalStrategy
        || props.knowledgeBase.chunkStrategy !== form.chunkStrategy
        || props.knowledgeBase.chunkSize !== form.chunkSize
        || props.knowledgeBase.chunkOverlap !== form.chunkOverlap
        || (props.knowledgeBase.separatorsJson || '') !== (form.separatorsJson || '')
      const confirmation = strategyChanged
        ? window.confirm('检测到检索或切片策略已变更，是否确认对 READY 文档重新处理？')
        : false
      await updateKnowledgeBase(props.knowledgeBase.id, form, confirmation || undefined)
    } else {
      await createKnowledgeBase(form)
    }
    emit('success')
    close()
  } catch (error) {
    window.alert(error instanceof Error ? error.message : '保存知识库失败')
  }
}
</script>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
}
.dialog {
  width: 560px;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}
form {
  display: grid;
  gap: 12px;
}
label {
  display: grid;
  gap: 6px;
}
input, textarea, select {
  padding: 8px;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>