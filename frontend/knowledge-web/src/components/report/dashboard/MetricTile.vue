<template>
  <article class="metric-tile">
    <span class="metric-tile__label">{{ label }}</span>
    <strong class="metric-tile__value">{{ value }}</strong>
    <p v-if="hint" class="metric-tile__hint">{{ hint }}</p>
  </article>
</template>

<script setup>
defineProps({
  label: {
    type: String,
    required: true,
  },
  value: {
    type: [String, Number],
    required: true,
  },
  hint: {
    type: String,
    default: '',
  },
});
</script>

<style scoped>
.metric-tile {
  position: relative;
  overflow: hidden;
  min-height: 128px;
  padding: 18px 20px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(13, 24, 44, 0.92) 0%, rgba(8, 15, 29, 0.96) 100%);
  border: 1px solid rgba(94, 179, 255, 0.2);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06), 0 0 28px rgba(61, 139, 255, 0.12);
}

.metric-tile::before {
  content: '';
  position: absolute;
  inset: -1px;
  border-radius: inherit;
  background: linear-gradient(135deg, rgba(55, 242, 177, 0.18), rgba(61, 139, 255, 0.08), transparent 65%);
  pointer-events: none;
}

.metric-tile__label,
.metric-tile__value,
.metric-tile__hint {
  position: relative;
  z-index: 1;
}

.metric-tile__label {
  display: block;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--pt-text-secondary);
}

.metric-tile__value {
  display: block;
  margin-top: 12px;
  font-size: 34px;
  line-height: 1;
  color: #f6fbff;
  text-shadow: 0 0 14px rgba(61, 139, 255, 0.28);
}

.metric-tile__hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: rgba(226, 238, 255, 0.72);
}
</style>

