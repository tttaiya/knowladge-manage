<template>
  <main class="config-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">知识管理</p>
        <h1>系统配置</h1>
      </div>
      <el-button :loading="loading" @click="loadAllConfigs">刷新</el-button>
    </header>

    <section class="config-grid">
      <el-card class="config-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>嵌入模型配置</span>
            <el-tag type="success" effect="plain">US6.1</el-tag>
          </div>
        </template>

        <el-form label-position="top" :model="embeddingForm">
          <el-form-item label="模型名称" required>
            <el-input v-model.trim="embeddingForm.model" placeholder="请输入嵌入模型名称" />
          </el-form-item>
          <el-form-item label="API 地址">
            <el-input v-model.trim="embeddingForm.apiBase" placeholder="https://example.com/embedding（选填）" clearable />
            <!-- R6/v6：默认 apiBase/apiKey 留空字符串（容器内 localhost 陷阱） -->
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="embeddingForm.apiKey" type="password" show-password placeholder="********" autocomplete="new-password" />
            <!-- R6/v6：默认空字符串 / 已掩码 -->
          </el-form-item>
          <el-form-item label="向量维度" required>
            <el-input-number v-model="embeddingForm.dimension" :min="1" :step="1" controls-position="right" />
            <!-- 默认 1024 由后端返回 -->
          </el-form-item>
        </el-form>

        <div class="actions">
          <el-button :loading="testing === 'embedding'" @click="testConnection('embedding')">测试连接</el-button>
          <el-button type="primary" :loading="saving === 'embedding'" @click="saveEmbeddingConfig">保存</el-button>
        </div>
      </el-card>

      <el-card class="config-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>重排序模型配置</span>
            <el-tag type="warning" effect="plain">US6.2</el-tag>
          </div>
        </template>

        <el-form label-position="top" :model="rerankForm">
          <el-form-item label="模型名称" required>
            <el-input v-model.trim="rerankForm.model" placeholder="请输入重排序模型名称" />
          </el-form-item>
          <el-form-item label="API 地址">
            <el-input v-model.trim="rerankForm.apiBase" placeholder="https://example.com/rerank（选填）" clearable />
            <!-- R6/v6：默认 apiBase/apiKey 留空字符串 -->
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="rerankForm.apiKey" type="password" show-password placeholder="********" autocomplete="new-password" />
            <!-- R6/v6：默认空字符串 / 已掩码 -->
          </el-form-item>
          <el-form-item label="Top N" required>
            <el-input-number v-model="rerankForm.topN" :min="1" :step="1" controls-position="right" />
            <!-- 默认 10 由后端返回 -->
          </el-form-item>
          <el-form-item label="阈值" required>
            <el-input-number v-model="rerankForm.threshold" :min="0" :max="1" :step="0.01" controls-position="right" />
            <!-- 默认 0.7 由后端返回 -->
          </el-form-item>
        </el-form>

        <div class="actions">
          <el-button :loading="testing === 'rerank'" @click="testConnection('rerank')">测试连接</el-button>
          <el-button type="primary" :loading="saving === 'rerank'" @click="saveRerankConfig">保存</el-button>
        </div>
      </el-card>

      <el-card class="config-card parser-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>解析器 / OCR / Worker 配置</span>
            <el-tag type="info" effect="plain">US4.4</el-tag>
          </div>
        </template>

        <el-form label-position="top" :model="parserForm">
          <el-form-item label="PaddleOCR 开关" required>
            <el-switch v-model="parserForm.paddleocrEnabled" active-text="开启" inactive-text="关闭" />
          </el-form-item>
          <el-form-item label="最大并发任务数" required>
            <el-input-number v-model="parserForm.maxConcurrentTasks" :min="1" :step="1" controls-position="right" />
            <!-- 默认 4 由后端返回；Worker DynamicConfigHolder 热加载，R28 -->
          </el-form-item>
          <el-form-item label="最大重试次数" required>
            <el-input-number v-model="parserForm.maxRetryCount" :min="0" :step="1" controls-position="right" />
            <!-- 默认 3 由后端返回 -->
          </el-form-item>
          <el-form-item label="超时时间（秒）" required>
            <el-input-number v-model="parserForm.timeoutSeconds" :min="1" :step="1" controls-position="right" />
            <!-- 默认 30 由后端返回 -->
          </el-form-item>
        </el-form>

        <div class="actions">
          <!-- R3/v6：OCR 测试按钮 P0 阶段隐藏（钱小晓实现错误：用 embeddingForm.apiBase 测 OCR）；等真实 OCR 地址配置后启用 -->
          <!-- <el-button :loading="testing === 'ocr'" @click="testConnection('ocr')">测试连接</el-button> -->
          <el-button type="primary" :loading="saving === 'parser'" @click="saveParserConfig">保存</el-button>
        </div>
      </el-card>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getEmbeddingConfig,
  getParserConfig,
  getRerankConfig,
  testConfigConnection,
  updateEmbeddingConfig,
  updateParserConfig,
  updateRerankConfig,
} from '@/api/modules/config'

const loading = ref(false)
const saving = ref('')
const testing = ref('')

const embeddingForm = reactive({
  model: 'text-embedding-v1',
  apiBase: '',
  apiKey: '',
  dimension: 1024,
})

const rerankForm = reactive({
  model: 'bge-reranker-v2-m3',
  apiBase: '',
  apiKey: '',
  topN: 10,
  threshold: 0.7,
})

const parserForm = reactive({
  paddleocrEnabled: false,
  maxConcurrentTasks: 4,
  maxRetryCount: 3,
  timeoutSeconds: 30,
})

function isValidUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return Boolean(url.protocol && url.host)
  } catch {
    return false
  }
}

function assertEmbedding(): boolean {
  if (!embeddingForm.model || !Number.isInteger(embeddingForm.dimension) || embeddingForm.dimension <= 0) {
    ElMessage.warning('请检查嵌入模型名称和向量维度')
    return false
  }
  if (embeddingForm.apiBase && !isValidUrl(embeddingForm.apiBase)) {
    ElMessage.warning('嵌入 API 地址格式非法')
    return false
  }
  return true
}

function assertRerank(): boolean {
  if (!rerankForm.model || !Number.isInteger(rerankForm.topN) || rerankForm.topN <= 0) {
    ElMessage.warning('请检查重排序模型名称和 Top N')
    return false
  }
  if (typeof rerankForm.threshold !== 'number' || rerankForm.threshold < 0 || rerankForm.threshold > 1) {
    ElMessage.warning('阈值必须在 0 到 1 之间')
    return false
  }
  if (rerankForm.apiBase && !isValidUrl(rerankForm.apiBase)) {
    ElMessage.warning('重排序 API 地址格式非法')
    return false
  }
  return true
}

function assertParser(): boolean {
  if (!Number.isInteger(parserForm.maxConcurrentTasks) || parserForm.maxConcurrentTasks <= 0) {
    ElMessage.warning('最大并发任务数必须为正整数')
    return false
  }
  if (!Number.isInteger(parserForm.maxRetryCount) || parserForm.maxRetryCount < 0) {
    ElMessage.warning('最大重试次数必须为非负整数')
    return false
  }
  if (!Number.isInteger(parserForm.timeoutSeconds) || parserForm.timeoutSeconds <= 0) {
    ElMessage.warning('超时时间必须为正整数')
    return false
  }
  return true
}

async function loadAllConfigs() {
  loading.value = true
  try {
    const [embedding, rerank, parser] = await Promise.all([
      getEmbeddingConfig(),
      getRerankConfig(),
      getParserConfig(),
    ])
    Object.assign(embeddingForm, embedding)
    Object.assign(rerankForm, rerank)
    Object.assign(parserForm, parser)
  } catch (error: any) {
    ElMessage.error(error?.message || '配置加载失败')
  } finally {
    loading.value = false
  }
}

async function saveEmbeddingConfig() {
  if (!assertEmbedding()) return
  saving.value = 'embedding'
  try {
    await updateEmbeddingConfig({ ...embeddingForm })
    ElMessage.success('配置已保存并即时生效')
    await loadAllConfigs()
  } catch (error: any) {
    ElMessage.error(error?.message || '嵌入模型配置保存失败')
  } finally {
    saving.value = ''
  }
}

async function saveRerankConfig() {
  if (!assertRerank()) return
  saving.value = 'rerank'
  try {
    await updateRerankConfig({ ...rerankForm })
    ElMessage.success('配置已保存并即时生效')
    await loadAllConfigs()
  } catch (error: any) {
    ElMessage.error(error?.message || '重排序模型配置保存失败')
  } finally {
    saving.value = ''
  }
}

async function saveParserConfig() {
  if (!assertParser()) return
  saving.value = 'parser'
  try {
    await updateParserConfig({ ...parserForm })
    ElMessage.success('配置已保存并即时生效')
    await loadAllConfigs()
  } catch (error: any) {
    ElMessage.error(error?.message || '解析器配置保存失败')
  } finally {
    saving.value = ''
  }
}

async function testConnection(type: 'embedding' | 'rerank') {
  const target = type === 'embedding' ? embeddingForm : rerankForm
  if (!target.apiBase) {
    ElMessage.warning('请先填写 API 地址')
    return
  }
  if (!isValidUrl(target.apiBase)) {
    ElMessage.warning('API 地址格式非法')
    return
  }
  testing.value = type
  try {
    const result = await testConfigConnection({
      type,
      apiBase: target.apiBase,
      apiKey: target.apiKey,
    })
    result.success ? ElMessage.success(result.message) : ElMessage.error(result.message)
  } catch (error: any) {
    ElMessage.error(error?.message || '连接测试失败')
  } finally {
    testing.value = ''
  }
}

onMounted(loadAllConfigs)
</script>

<style scoped>
.config-page {
  min-height: 100vh;
  padding: 32px;
  background: #eef2f7;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  max-width: 1180px;
  margin: 0 auto 24px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #64748b;
  font-size: 14px;
}

h1 {
  margin: 0;
  color: #111827;
  font-size: 28px;
  font-weight: 650;
  letter-spacing: 0;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto;
}

.config-card {
  border-radius: 8px;
  border-color: #dbe3ee;
  text-align: left;
}

.parser-card {
  grid-column: 1 / -1;
}

.card-header,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-header {
  color: #1f2937;
  font-weight: 650;
}

.actions {
  justify-content: flex-end;
  padding-top: 6px;
}

:deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 860px) {
  .config-page {
    padding: 20px;
  }

  .page-header {
    align-items: stretch;
    flex-direction: column;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }
}
</style>