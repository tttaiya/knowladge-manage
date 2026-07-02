<template>
  <el-dialog
    :model-value="visible"
    :title="mode === 'create' ? '新建知识库' : '编辑知识库'"
    width="640"
    @update:model-value="handleVisibleChange"
    @close="onClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="128" show-word-limit placeholder="1~128 字符" />
      </el-form-item>
      <el-form-item label="分类" prop="category">
        <el-select v-model="form.category" placeholder="选择分类" style="width: 100%">
          <el-option v-for="c in KB_CATEGORIES" :key="c.value" :label="c.label" :value="c.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="检索策略" prop="retrievalStrategy">
        <el-select v-model="form.retrievalStrategy" placeholder="选择检索策略" style="width: 100%">
          <el-option v-for="s in KB_RETRIEVAL_STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="切片策略" prop="chunkStrategy">
        <el-select v-model="form.chunkStrategy" placeholder="选择切片策略" style="width: 100%">
          <el-option v-for="s in KB_CHUNK_STRATEGIES" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="切片大小" prop="chunkSize">
        <el-input-number v-model="form.chunkSize" :min="50" :step="50" />
      </el-form-item>
      <el-form-item label="切片重叠" prop="chunkOverlap">
        <el-input-number v-model="form.chunkOverlap" :min="0" :step="10" />
      </el-form-item>
      <el-form-item label="分隔符" prop="separatorsJson">
        <el-input
          v-model="form.separatorsJson"
          type="textarea"
          :rows="2"
          placeholder='JSON 数组，例如 ["\n\n","\n","。"]'
        />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>

      <el-alert
        v-if="strategyChanged"
        type="warning"
        :closable="false"
        title="策略变更确认"
        description="保存后将为该知识库所有 READY 文档创建新的处理版本。旧的已审核版本会继续提供检索；新的处理版本将在解析、切片、向量化完成后进入待审核；审核通过后，新版本才会替换当前 READY 版本。"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-form-item v-if="strategyChanged" label="策略确认" prop="confirmation">
        <el-checkbox v-model="form.confirmation">
          我已了解策略变更的影响，确认继续
        </el-checkbox>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="onClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">
        {{ mode === 'create' ? '创建' : '保存' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createKnowledgeBase,
  updateKnowledgeBase,
  type KnowledgeBaseVO,
} from '@/api/modules/knowledge-base'
import {
  KB_CATEGORIES,
  KB_CHUNK_STRATEGIES,
  KB_RETRIEVAL_STRATEGIES,
} from '@/types/knowledge-base'

const props = defineProps<{
  visible: boolean
  kb: KnowledgeBaseVO | null
  mode: 'create' | 'edit'
}>()
const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

function handleVisibleChange(value: boolean) {
  emit('update:visible', value)
}

const form = reactive({
  name: '',
  category: 'GENERAL',
  retrievalStrategy: 'VECTOR_RERANK',
  chunkStrategy: 'HEADING',
  chunkSize: 500,
  chunkOverlap: 50,
  separatorsJson: '[]',
  description: '',
  confirmation: false,
})

const originalStrategy = ref<{ retrievalStrategy: string; chunkStrategy: string; chunkSize: number; chunkOverlap: number; separatorsJson: string } | null>(null)

const strategyChanged = computed(() => {
  if (props.mode !== 'edit' || !originalStrategy.value) return false
  const o = originalStrategy.value
  return (
    form.retrievalStrategy !== o.retrievalStrategy ||
    form.chunkStrategy !== o.chunkStrategy ||
    form.chunkSize !== o.chunkSize ||
    form.chunkOverlap !== o.chunkOverlap ||
    form.separatorsJson !== o.separatorsJson
  )
})

const rules: FormRules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' },
    { min: 1, max: 128, message: '长度 1~128 字符', trigger: 'blur' },
  ],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  retrievalStrategy: [{ required: true, message: '请选择检索策略', trigger: 'change' }],
  chunkStrategy: [{ required: true, message: '请选择切片策略', trigger: 'change' }],
  chunkSize: [{ required: true, type: 'number', min: 50, message: '切片大小 ≥ 50', trigger: 'blur' }],
  chunkOverlap: [{ required: true, type: 'number', min: 0, message: '切片重叠 ≥ 0', trigger: 'blur' }],
  separatorsJson: [
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        try {
          const parsed = JSON.parse(value)
          if (!Array.isArray(parsed)) return callback(new Error('必须是 JSON 数组'))
          callback()
        } catch (e: any) {
          callback(new Error('JSON 解析失败：' + e.message))
        }
      },
      trigger: 'blur',
    },
  ],
  confirmation: [
    {
      validator: (_rule, value, callback) => {
        if (strategyChanged.value && !value) {
          return callback(new Error('策略变更需勾选确认'))
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

function resetForm() {
  form.name = ''
  form.category = 'GENERAL'
  form.retrievalStrategy = 'VECTOR_RERANK'
  form.chunkStrategy = 'HEADING'
  form.chunkSize = 500
  form.chunkOverlap = 50
  form.separatorsJson = '[]'
  form.description = ''
  form.confirmation = false
  originalStrategy.value = null
}

function loadFromKb() {
  if (!props.kb) {
    resetForm()
    return
  }
  form.name = props.kb.name
  form.category = props.kb.category
  form.retrievalStrategy = props.kb.retrievalStrategy
  form.chunkStrategy = props.kb.chunkStrategy
  form.chunkSize = props.kb.chunkSize
  form.chunkOverlap = props.kb.chunkOverlap
  // 编辑时无 separatorsJson（listVO 没暴露）；从 detail 上下文可取，简化处理
  form.separatorsJson = (props.kb as any).separatorsJson || '[]'
  form.description = props.kb.description || ''
  form.confirmation = false
  originalStrategy.value = {
    retrievalStrategy: props.kb.retrievalStrategy,
    chunkStrategy: props.kb.chunkStrategy,
    chunkSize: props.kb.chunkSize,
    chunkOverlap: props.kb.chunkOverlap,
    separatorsJson: (props.kb as any).separatorsJson || '[]',
  }
}

watch(
  () => [props.visible, props.kb?.id, props.mode],
  () => {
    if (props.visible) {
      loadFromKb()
      // 清除旧校验
      setTimeout(() => formRef.value?.clearValidate(), 0)
    }
  },
)

function onClose() {
  emit('update:visible', false)
}

async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (strategyChanged.value && !form.confirmation) {
    ElMessage.warning('策略变更需勾选确认')
    return
  }
  submitting.value = true
  try {
    const payload = {
      name: form.name,
      category: form.category,
      retrievalStrategy: form.retrievalStrategy,
      chunkStrategy: form.chunkStrategy,
      chunkSize: form.chunkSize,
      chunkOverlap: form.chunkOverlap,
      separatorsJson: form.separatorsJson,
      description: form.description,
      confirmation: form.confirmation,
    }
    let resp
    if (props.mode === 'create') {
      resp = await createKnowledgeBase(payload)
    } else {
      resp = await updateKnowledgeBase(props.kb!.id, payload, form.confirmation || undefined)
    }
    if (resp.code === 0) {
      const successMessage =
        props.mode === 'create'
          ? '创建成功'
          : strategyChanged.value
            ? '已保存，并已为知识库文档创建 REPROCESS 任务'
            : '已保存'
      ElMessage.success(successMessage)
      emit('saved')
    } else {
      ElMessage.error(resp.message || '操作失败')
    }
  } catch (e: any) {
    if (e?.response?.data?.message) {
      ElMessage.error(e.response.data.message)
    } else if (e?.message && !e.message.includes('validate')) {
      ElMessage.error(e.message)
    }
  } finally {
    submitting.value = false
  }
}
</script>
