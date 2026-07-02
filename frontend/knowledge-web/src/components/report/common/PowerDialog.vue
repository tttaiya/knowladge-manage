<template>
  <el-dialog
    class="power-dialog"
    :model-value="modelValue"
    :title="title"
    :width="width"
    append-to-body
    :close-on-click-modal="false"
    @update:modelValue="$emit('update:modelValue', $event)"
  >
    <slot />
    <template #footer>
      <div class="power-dialog__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: Boolean,
  title: String,
  width: { type: String, default: '520px' },
});
</script>

<style scoped>
.power-dialog :deep(.el-dialog) {
  background: rgba(9, 14, 24, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 18px;
  box-shadow: var(--pt-shadow-lg);
  color: var(--pt-text-primary);
}
.power-dialog :deep(.el-dialog__header) {
  margin-right: 0;
  padding: 18px 24px 0;
}
.power-dialog :deep(.el-dialog__title) {
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--pt-text-secondary);
}
.power-dialog :deep(.el-dialog__body) {
  padding: 22px 24px 18px;
}
.power-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px 24px;
}
</style>

