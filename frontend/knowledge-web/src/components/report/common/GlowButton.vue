<template>
  <el-button
    class="glow-button"
    :loading="loading"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot />
  </el-button>
</template>

<script setup>
defineProps({
  loading: Boolean,
  disabled: Boolean,
});
</script>

<style scoped>
.glow-button :deep(.el-button) {
  background: linear-gradient(135deg, #37f2b1 0%, #3d8bff 100%);
  border: none;
  color: #04111f;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  padding: 12px 22px;
  border-radius: 14px;
  box-shadow: var(--pt-glow);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.glow-button :deep(.el-button:hover) {
  transform: translateY(-2px);
  box-shadow: 0 0 24px rgba(61, 139, 255, 0.55);
}
.glow-button :deep(.el-button.is-disabled) {
  opacity: 0.6;
  box-shadow: none;
}
</style>

