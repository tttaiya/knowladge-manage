<template>
  <el-tag
    :type="tagType"
    :effect="effect"
    size="small"
  >
    {{ label }}
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DocumentStatus } from '@/types/knowledge'

const props = withDefaults(
  defineProps<{
    status: DocumentStatus | string
    effect?: 'light' | 'dark' | 'plain'
  }>(),
  { effect: 'light' },
)

const labelMap: Record<string, string> = {
  UPLOADED: '已上传',
  PARSING: '解析中',
  CHUNKING: '切片中',
  VECTORIZING: '向量化中',
  PENDING_REVIEW: '待审核',
  READY: '就绪',
  REVIEW_REJECTED: '审核未通过',
  FAILED: '失败',
  DELETED: '已删除',
}

const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | 'primary'> = {
  UPLOADED: 'info',
  PARSING: 'warning',
  CHUNKING: 'warning',
  VECTORIZING: 'warning',
  PENDING_REVIEW: 'primary',
  READY: 'success',
  REVIEW_REJECTED: 'danger',
  FAILED: 'danger',
  DELETED: 'info',
}

const label = computed(() => labelMap[props.status] || props.status)
const tagType = computed(() => typeMap[props.status] || 'info')
</script>
