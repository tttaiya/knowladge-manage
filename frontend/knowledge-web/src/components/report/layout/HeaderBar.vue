<template>
  <header class="header-bar">
    <div class="header-copy">
      <strong>{{ title }}</strong>
      <span v-if="subtitle">{{ subtitle }}</span>
    </div>

    <div class="header-actions">
      <el-tag :type="online ? 'success' : 'info'" effect="dark" class="status-tag">
        {{ backendLabel }}
      </el-tag>
      <el-tag effect="dark" class="status-tag">{{ userLabel }}</el-tag>
      <el-button size="small" :loading="loading" @click="emit('refresh-backend')">刷新后端</el-button>
      <el-button size="small" @click="emit('logout')">退出</el-button>
    </div>
  </header>
</template>

<script setup>
defineProps({
  title: {
    type: String,
    required: true
  },
  subtitle: {
    type: String,
    required: true
  },
  online: {
    type: Boolean,
    default: false
  },
  backendLabel: {
    type: String,
    required: true
  },
  userLabel: {
    type: String,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['refresh-backend', 'logout']);
</script>

<style scoped>
.header-bar {
  min-height: 72px;
  padding: 16px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: linear-gradient(180deg, rgba(9, 15, 27, 0.76), rgba(12, 19, 34, 0.92));
  backdrop-filter: blur(24px);
}

.header-copy strong,
.header-copy span {
  display: block;
}

.header-copy strong {
  color: #eef6ff;
  font-size: 18px;
  font-weight: 800;
}

.header-copy span {
  margin-top: 4px;
  color: #87a0c7;
  font-size: 12px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.status-tag {
  border: 0;
}
</style>


