<template>
  <el-dialog
    :model-value="modelValue"
    title="编辑分块"
    width="720px"
    @close="close"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="chunk" class="chunk-summary">
      <span>序号 {{ chunk.chunkIndex }}</span>
      <span v-if="chunk.chapterPath">{{ chunk.chapterPath }}</span>
      <span v-if="chunk.pageNo">第 {{ chunk.pageNo }} 页</span>
      <span v-if="chunk.chunkType">{{ chunk.chunkType }}</span>
      <span>{{ draft.length }} 字</span>
    </div>
    <el-input v-model="draft" type="textarea" :rows="12" resize="vertical" />
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="save">保存并重向量化</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ReviewChunk } from '@/api/modules/review'

const props = defineProps<{
  modelValue: boolean
  chunk: ReviewChunk | null
  submitting: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'save', content: string): void
}>()

const draft = ref('')

watch(
  () => props.chunk,
  (chunk) => {
    draft.value = chunk?.content || ''
  },
  { immediate: true },
)

function close() {
  emit('update:modelValue', false)
}

function save() {
  if (!draft.value.trim()) {
    ElMessage.warning('分块正文不能为空')
    return
  }
  emit('save', draft.value)
}
</script>

<style scoped>
.chunk-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
