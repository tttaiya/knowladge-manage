<template>
  <el-tabs class="power-tabs" v-bind="$attrs" @tab-change="$emit('tab-change', $event)">
    <slot />
  </el-tabs>
</template>

<style scoped>
.power-tabs :deep(.el-tabs__header) {
  margin-bottom: 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}
.power-tabs :deep(.el-tabs__item) {
  color: var(--pt-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
}
.power-tabs :deep(.is-active) {
  color: var(--pt-text-primary);
}
.power-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, #37f2b1, #3d8bff);
}
</style>

