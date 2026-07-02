<template>
  <span :class="['status-pill', `status-pill--${variant}`]">
    <slot />
  </span>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'info',
    validator: (value) => ['info', 'success', 'warning', 'danger', 'offline'].includes(value),
  },
});
</script>

<style scoped>
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  background: rgba(233, 241, 255, 0.08);
  border: 1px solid rgba(233, 241, 255, 0.25);
  text-transform: uppercase;
}
.status-pill--success {
  background: rgba(55, 242, 177, 0.16);
  border-color: rgba(55, 242, 177, 0.4);
  color: #9cfad6;
}
.status-pill--warning {
  background: rgba(255, 183, 85, 0.18);
  border-color: rgba(255, 183, 85, 0.45);
  color: #ffd7a5;
}
.status-pill--danger {
  background: rgba(255, 107, 129, 0.18);
  border-color: rgba(255, 107, 129, 0.45);
  color: #ffb1c0;
}
.status-pill--offline {
  background: rgba(91, 108, 142, 0.22);
  border-color: rgba(91, 108, 142, 0.45);
  color: #b6c4db;
}
</style>

