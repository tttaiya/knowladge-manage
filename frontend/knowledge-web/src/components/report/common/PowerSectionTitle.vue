<template>
  <header class="section-title">
    <div class="section-title__main">
      <span v-if="icon" class="section-title__icon">
        <component :is="icon" />
      </span>
      <div>
        <h2>{{ title }}</h2>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div class="section-title__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: String,
  icon: [String, Object],
});
</script>

<style scoped>
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  margin-bottom: 18px;
}
.section-title__main {
  display: flex;
  gap: 14px;
  align-items: center;
}
.section-title__icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  background: rgba(61, 139, 255, 0.18);
  color: var(--pt-brand);
  box-shadow: inset 0 0 18px rgba(61, 139, 255, 0.4);
}
.section-title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}
.section-title p {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--pt-text-secondary);
}
.section-title__actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>

