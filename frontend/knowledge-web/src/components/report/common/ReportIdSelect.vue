<template>
  <el-select
    v-model="selectedId"
    filterable
    clearable
    class="report-id-select"
    placeholder="选择报告编号"
    :loading="loading"
  >
    <el-option
      v-for="item in options"
      :key="item.id"
      :label="item.label"
      :value="item.id"
    />
  </el-select>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { reportApi } from '@/api/modules/report';

const props = defineProps({
  modelValue: {
    type: Number,
    default: 1,
  },
});

const emit = defineEmits(['update:modelValue', 'change']);

const records = ref([]);
const loading = ref(false);

const selectedId = computed({
  get: () => props.modelValue,
  set: (value) => {
    const nextValue = value ? Number(value) : null;
    emit('update:modelValue', nextValue);
    emit('change', nextValue);
  },
});

const options = computed(() =>
  records.value.map((record) => ({
    id: Number(record.id),
    label: `${record.id}${record.reportName ? ` · ${record.reportName}` : ''}${record.status !== undefined ? ` · 状态${record.status}` : ''}`,
  }))
);

async function loadRecords() {
  loading.value = true;
  try {
    records.value = await reportApi.listRecords();
  } finally {
    loading.value = false;
  }
}

onMounted(loadRecords);

watch(
  () => props.modelValue,
  (value) => {
    if (value && !records.value.some((record) => Number(record.id) === Number(value))) {
      loadRecords();
    }
  }
);
</script>

<style scoped>
.report-id-select {
  width: 240px;
}

.report-id-select :deep(.el-input__wrapper) {
  background: rgba(10, 18, 34, 0.72);
  border: 1px solid rgba(174, 197, 255, 0.18);
  box-shadow: none;
  border-radius: 14px;
}

.report-id-select :deep(.el-input__inner) {
  color: var(--pt-text-primary);
}

.report-id-select :deep(.el-select__caret) {
  color: var(--pt-text-secondary);
}

.report-id-select :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(61, 139, 255, 0.55);
}

.report-id-select :deep(.el-input__wrapper:hover) {
  border-color: rgba(61, 139, 255, 0.38);
}
</style>


