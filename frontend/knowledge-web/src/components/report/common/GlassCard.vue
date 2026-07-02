<template>
  <section :class="['glass-card', `glass-card--${variant}`]">
    <header v-if="$slots.header" class="glass-card__header">
      <slot name="header" />
    </header>
    <div class="glass-card__body">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="glass-card__footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'panel',
    validator: (value) => ['panel', 'section', 'inline'].includes(value),
  },
});
</script>

<style scoped>
.glass-card {
  background: var(--pt-surface);
  border: 1px solid var(--pt-surface-border);
  border-radius: 16px;
  box-shadow: var(--pt-shadow-lg);
  backdrop-filter: blur(24px);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  color: var(--pt-text-primary);
}
.glass-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--pt-shadow-lg-hover);
}
.glass-card__header,
.glass-card__footer {
  padding: 18px 24px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.glass-card__footer {
  border-bottom: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}
.glass-card__body {
  padding: 22px 24px;
}
.glass-card--section .glass-card__body {
  padding: 20px;
}
.glass-card--inline .glass-card__body {
  padding: 16px 18px;
}
</style>

