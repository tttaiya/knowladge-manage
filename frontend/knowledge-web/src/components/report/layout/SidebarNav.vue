<template>
  <aside class="sidebar-nav">
    <div class="sidebar-brand">
      <div class="brand-mark">电技</div>
      <div class="brand-copy">
        <strong>电力技术监督智能辅助平台</strong>
        
      </div>
    </div>

    <nav class="sidebar-menu" aria-label="主导航">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="sidebar-item"
        :class="{ active: item.key === activeKey }"
        @click="emit('select', item.key)"
      >
        <span class="item-indicator" />
        <span class="item-icon">{{ item.icon }}</span>
        <span class="item-label">{{ item.label }}</span>
      </button>
    </nav>
  </aside>
</template>

<script setup>
defineProps({
  activeKey: {
    type: String,
    default: 'dashboard'
  }
});

const emit = defineEmits(['select']);

const items = [
  { key: 'dashboard', label: '工作台', icon: '⌂' },
  { key: 'create', label: '报告创建', icon: '＋' },
  { key: 'outline', label: '大纲管理', icon: '☰' },
  { key: 'generation', label: '正文生成', icon: '✦' },
  { key: 'editor', label: '报告编辑', icon: '✎' },
  { key: 'records', label: '报告记录', icon: '⌁' },
  { key: 'templates', label: '模板管理', icon: '▦' }
];
</script>

<style scoped>
.sidebar-nav {
  width: 220px;
  padding: 20px 16px;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(9, 15, 27, 0.78), rgba(13, 21, 37, 0.9));
  backdrop-filter: blur(24px);
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.04);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, rgba(55, 242, 177, 0.95), rgba(61, 139, 255, 0.9));
  color: #06111f;
  font-weight: 900;
  box-shadow: 0 0 20px rgba(55, 242, 177, 0.28);
}

.brand-copy {
  min-width: 0;
}

.brand-copy strong,
.brand-copy span {
  display: block;
}

.brand-copy strong {
  color: #edf4ff;
  font-size: 14px;
}

.brand-copy span {
  margin-top: 4px;
  color: #89a0c8;
  font-size: 12px;
}

.sidebar-menu {
  display: grid;
  gap: 8px;
}

.sidebar-item {
  position: relative;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 16px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #b9c7e0;
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}

.sidebar-item:hover {
  transform: translateY(-1px);
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(55, 242, 177, 0.16);
  box-shadow: 0 10px 24px rgba(11, 23, 44, 0.18);
}

.sidebar-item.active {
  color: #eef7ff;
  background: linear-gradient(135deg, rgba(55, 242, 177, 0.16), rgba(61, 139, 255, 0.12));
  border-color: rgba(55, 242, 177, 0.28);
  box-shadow: 0 0 0 1px rgba(55, 242, 177, 0.08), 0 14px 30px rgba(11, 23, 44, 0.24);
}

.item-indicator {
  width: 4px;
  height: 18px;
  border-radius: 999px;
  background: transparent;
  box-shadow: 0 0 0 rgba(55, 242, 177, 0);
}

.sidebar-item.active .item-indicator {
  background: linear-gradient(180deg, #37f2b1, #3d8bff);
  box-shadow: 0 0 14px rgba(55, 242, 177, 0.9);
}

.item-icon {
  width: 20px;
  display: inline-flex;
  justify-content: center;
  color: #37f2b1;
  font-weight: 700;
}

.item-label {
  font-size: 14px;
  font-weight: 600;
}
</style>



