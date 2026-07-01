<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  UPLOAD_ACCEPT,
  UPLOAD_LIMITS,
  normalizeTags,
  parseTagsInput,
  uploadDocuments,
  validateTags,
  validateUploadFile,
} from '@/api/modules/document'

const props = defineProps<{
  kbId: number
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'success'): void
  (e: 'update:visible', value: boolean): void
}>()

function closeDialog() {
  emit('update:visible', false)
}

interface QueueItem {
  uid: string
  file: File
  status: 'pending' | 'uploading' | 'success' | 'error'
  progress: number
  error?: string
}

const fileInputRef = ref<HTMLInputElement>()
const tagInput = ref('')
const queue = ref<QueueItem[]>([])
const uploading = ref(false)
const isDragOver = ref(false)
const dragDepth = ref(0)

const parsedTags = computed(() => normalizeTags(parseTagsInput(tagInput.value)))
const pendingCount = computed(() => queue.value.filter((item) => item.status === 'pending').length)

function addValidFile(file: File) {
  const exists = queue.value.some(
    (item) => item.file.name === file.name && item.file.size === file.size,
  )
  if (exists) {
    return false
  }
  queue.value.push({
    uid: `${file.name}-${file.size}-${Date.now()}-${Math.random()}`,
    file,
    status: 'pending',
    progress: 0,
  })
  return true
}

/** 统一入库：格式校验、数量限制（点击选择与拖拽共用） */
function ingestFiles(files: File[], options: { silent?: boolean } = {}) {
  if (!files.length) {
    if (!options.silent) {
      ElMessage.warning('未识别到可上传的文件')
    }
    return 0
  }

  let added = 0
  let skipped = 0

  for (const file of files) {
    if (queue.value.length >= UPLOAD_LIMITS.maxBatchCount) {
      ElMessage.warning(`单次最多上传 ${UPLOAD_LIMITS.maxBatchCount} 个文件`)
      break
    }

    const error = validateUploadFile(file)
    if (error) {
      skipped++
      continue
    }
    if (addValidFile(file)) {
      added++
    }
  }

  if (!options.silent) {
    if (added > 0) {
      const skipHint = skipped > 0 ? `，已跳过 ${skipped} 个不支持或重复的文件` : ''
      ElMessage.success(`已识别 ${added} 个文件${skipHint}`)
    } else if (skipped > 0) {
      ElMessage.error('暂不支持该格式，请上传 PDF、DOCX、PPTX、XLSX、MD、TXT、PNG、JPG、JPEG')
    }
  }

  return added
}

async function readAllEntries(reader: FileSystemDirectoryReader): Promise<FileSystemEntry[]> {
  const entries: FileSystemEntry[] = []
  let batch: FileSystemEntry[]
  do {
    batch = await new Promise<FileSystemEntry[]>((resolve, reject) =>
      reader.readEntries(resolve, reject),
    )
    entries.push(...batch)
  } while (batch.length > 0)
  return entries
}

async function readEntryFiles(entry: FileSystemEntry): Promise<File[]> {
  if (entry.isFile) {
    return new Promise<File[]>((resolve) => {
      ;(entry as FileSystemFileEntry).file(
        (file) => resolve([file]),
        () => resolve([]),
      )
    })
  }
  if (entry.isDirectory) {
    const reader = (entry as FileSystemDirectoryEntry).createReader()
    const children = await readAllEntries(reader)
    const nested = await Promise.all(children.map((child) => readEntryFiles(child)))
    return nested.flat()
  }
  return []
}

/** US3.2：解析拖拽的文件/文件夹 */
async function collectDroppedFiles(dataTransfer: DataTransfer | null): Promise<File[]> {
  if (!dataTransfer) {
    return []
  }

  const collected: File[] = []
  const items = dataTransfer.items
  if (items?.length) {
    for (const item of Array.from(items)) {
      if (item.kind !== 'file') {
        continue
      }
      const entry = item.webkitGetAsEntry?.()
      if (entry) {
        collected.push(...(await readEntryFiles(entry)))
        continue
      }
      const file = item.getAsFile()
      if (file) {
        collected.push(file)
      }
    }
  }
  if (collected.length) {
    return collected
  }

  return Array.from(dataTransfer.files)
}

function triggerFileSelect() {
  if (uploading.value) {
    return
  }
  fileInputRef.value?.click()
}

function onNativeFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  if (!input.files?.length) {
    return
  }
  ingestFiles(Array.from(input.files))
  input.value = ''
}

function onDragEnter(event: DragEvent) {
  event.preventDefault()
  dragDepth.value += 1
  isDragOver.value = true
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'copy'
  }
  isDragOver.value = true
}

function onDragLeave(event: DragEvent) {
  event.preventDefault()
  dragDepth.value = Math.max(0, dragDepth.value - 1)
  if (dragDepth.value === 0) {
    isDragOver.value = false
  }
}

/** US3.2：拖入后识别文件并自动触发上传 */
async function onDrop(event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  dragDepth.value = 0
  isDragOver.value = false

  if (uploading.value) {
    return
  }

  const files = await collectDroppedFiles(event.dataTransfer)
  const added = ingestFiles(files)
  if (added > 0) {
    await handleUpload()
  }
}

function removeItem(uid: string) {
  queue.value = queue.value.filter((item) => item.uid !== uid)
}

function clearQueue() {
  queue.value = []
}

async function handleUpload() {
  const validItems = queue.value.filter((item) => item.status === 'pending')
  if (!validItems.length) {
    ElMessage.warning('请先选择或拖入要上传的文件')
    return
  }

  const tags = parsedTags.value
  const tagValidationError = validateTags(tags)
  if (tagValidationError) {
    ElMessage.warning(tagValidationError)
    return
  }

  uploading.value = true
  try {
    validItems.forEach((item) => {
      item.status = 'uploading'
      item.progress = 40
    })

    const files = validItems.map((item) => item.file)
    await uploadDocuments(props.kbId, files, tags)

    validItems.forEach((item) => {
      item.status = 'success'
      item.progress = 100
    })

    const tagHint = tags.length ? `，标签：${tags.join('、')}` : ''
    ElMessage.success(`成功上传 ${validItems.length} 个文档${tagHint}`)
    emit('success')
    closeDialog()
    clearQueue()
    tagInput.value = ''
  } catch (error: any) {
    const message: string = error?.message ?? '上传失败'
    validItems.forEach((item) => {
      item.status = 'error'
      item.error = message
    })
    ElMessage.error(message)
  } finally {
    uploading.value = false
  }
}

function handleClose() {
  if (!uploading.value) {
    clearQueue()
    tagInput.value = ''
    isDragOver.value = false
    dragDepth.value = 0
  }
}
</script>

<template>
  <el-dialog
    :model-value="props.visible"
    title="上传文档"
    width="640px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
    @close="handleClose"
  >
    <!-- US3.2：统一拖拽区域（避免与 el-upload 内置拖拽冲突） -->
    <div
      class="drop-zone"
      :class="{ 'is-dragover': isDragOver, 'is-uploading': uploading }"
      @dragenter="onDragEnter"
      @dragover="onDragOver"
      @dragleave="onDragLeave"
      @drop="onDrop"
    >
      <el-icon class="drop-icon"><UploadFilled /></el-icon>
      <p class="drop-title">将文件或文件夹拖到此处，将自动开始上传</p>
      <p class="drop-subtitle">也可点击下方按钮选择文件，选择后需手动点击「开始上传」</p>

      <button type="button" class="native-upload-btn" :disabled="uploading" @click="triggerFileSelect">
        选择文件
      </button>
      <input
        ref="fileInputRef"
        type="file"
        multiple
        hidden
        :accept="UPLOAD_ACCEPT"
        @change="onNativeFileChange"
      />

      <p class="hint">
        支持 PDF、DOCX、PPTX、XLSX、MD、TXT、PNG、JPG、JPEG；单文件 ≤ 50MB，单次最多 10 个
      </p>
    </div>

    <el-form label-width="80px" style="margin-top: 16px">
      <el-form-item label="标签">
        <el-input
          v-model="tagInput"
          placeholder="多个标签以逗号分隔，如：锅炉,规程,安全"
          clearable
          :disabled="uploading"
        />
        <p class="tag-hint">拖拽上传前可先填写标签；拖入后将连同标签一并入库。</p>
        <div v-if="parsedTags.length" class="tag-preview">
          <span class="preview-label">将写入：</span>
          <el-tag
            v-for="tag in parsedTags"
            :key="tag"
            size="small"
            type="info"
            style="margin: 2px"
          >
            {{ tag }}
          </el-tag>
        </div>
      </el-form-item>
    </el-form>

    <div v-if="queue.length" class="queue-list">
      <div class="queue-head">上传队列（{{ pendingCount }} 待上传）</div>
      <div v-for="item in queue" :key="item.uid" class="queue-item">
        <div class="queue-meta">
          <span class="filename">{{ item.file.name }}</span>
          <span class="size">{{ (item.file.size / 1024 / 1024).toFixed(2) }} MB</span>
          <el-button link type="danger" :disabled="uploading" @click="removeItem(item.uid)">
            移除
          </el-button>
        </div>
        <el-progress
          v-if="item.status === 'uploading' || item.status === 'success'"
          :percentage="item.progress"
          :status="item.status === 'success' ? 'success' : undefined"
        />
        <p v-if="item.error" class="error-text">{{ item.error }}</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="closeDialog" :disabled="uploading">取消</el-button>
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="!pendingCount"
        @click="handleUpload"
      >
        开始上传
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.drop-zone {
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  padding: 24px 20px;
  text-align: center;
  background: var(--el-fill-color-light);
  transition: border-color 0.2s, background-color 0.2s;
}

.drop-zone.is-dragover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.drop-zone.is-uploading {
  pointer-events: none;
  opacity: 0.85;
}

.drop-icon {
  font-size: 36px;
  color: var(--el-color-primary);
}

.drop-title {
  margin: 8px 0 4px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.drop-subtitle {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.native-upload-btn {
  display: inline-block;
  margin-top: 4px;
  padding: 8px 20px;
  color: #fff;
  background: var(--el-color-primary);
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.native-upload-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.hint {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.queue-list {
  margin-top: 16px;
  max-height: 220px;
  overflow-y: auto;
}

.queue-head {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.queue-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.queue-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filename {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.size {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.error-text {
  margin: 4px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}

.tag-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.tag-preview {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

.preview-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
