import { del, get, post, put } from '@/api/request'
import type { DocumentChunk, DocumentItem, PageResult } from '@/types/knowledge'

/**
 * 文档分页列表。
 * v4 计划：保留程雨彤原代码风格，调用方直接拿 records（不关心 envelope）。
 * 所以这里 .then(r => r.data) 一次解包，页面代码不用改。
 */
export function fetchDocuments(params: {
  kbId: number
  status?: string
  keyword?: string
  page?: number
  pageSize?: number
}) {
  const { kbId, ...query } = params
  return get<PageResult<DocumentItem>>(`/knowledge-bases/${kbId}/documents`, { params: query }).then(
    (r) => r.data,
  )
}

/** 上传文档（US3.3：可同时携带标签，写入 km_document_tag） */
export function uploadDocuments(kbId: number, files: File[], tags: string[] = []) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  normalizeTags(tags).forEach((tag) => formData.append('tags', tag))
  return post<DocumentItem[]>(`/knowledge-bases/${kbId}/documents/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then((r) => r.data)
}

/** 编辑文档标签（US3.11：更新后同步影响检索标签过滤） */
export function updateDocumentTags(docId: number, tags: string[]) {
  return put<void>(`/documents/${docId}/tags`, { tags: normalizeTags(tags) }).then((r) => r.data)
}

/** US3.7 删除单个文档（逻辑删除 → 回收站，向量同步失效） */
export function deleteDocument(docId: number) {
  return del<void>(`/documents/${docId}`).then((r) => r.data)
}

/** 批量删除文档 */
export function batchDeleteDocuments(ids: number[]) {
  return post<void>('/documents/batch-delete', ids).then((r) => r.data)
}

/** 下载原文 */
export function downloadDocument(docId: number) {
  return `/api/v1/documents/${docId}/download`
}

/** 回收站列表 */
export function fetchRecycleBin(kbId: number, page = 1, pageSize = 20) {
  return get<PageResult<DocumentItem>>(`/knowledge-bases/${kbId}/documents/recycle-bin`, {
    params: { page, pageSize },
  }).then((r) => r.data)
}

/** 恢复文档 */
export function restoreDocument(docId: number) {
  return post<void>(`/documents/${docId}/restore`).then((r) => r.data)
}

/**
 * 永久删除。
 * R8：当前 v4 阶段 Worker MinIO 删除未就绪，后端会创建 PURGE 任务由 Worker 异步清理。
 * 前端入口仍保留；UI 层在 DocumentList 阶段可考虑隐藏按钮，由提交方决定。
 */
export function permanentDeleteDocument(docId: number) {
  return del<void>(`/documents/${docId}/permanent`).then((r) => r.data)
}

/** 查看切片详情（US3.9，READY 状态可用） */
export function fetchDocumentChunks(docId: number, page = 1, pageSize = 20) {
  return get<PageResult<DocumentChunk>>(`/documents/${docId}/chunks`, {
    params: { page, pageSize },
  }).then((r) => r.data)
}

/** 前端上传校验常量（与设计文档一致） */
export const UPLOAD_LIMITS = {
  maxFileSizeMb: 50,
  maxBatchCount: 10,
  allowedExtensions: ['pdf', 'docx', 'pptx', 'xlsx', 'md', 'txt', 'png', 'jpg', 'jpeg'],
} as const

/** 文件选择框 accept 属性，限制可选类型 */
export const UPLOAD_ACCEPT = '.pdf,.docx,.pptx,.xlsx,.md,.txt,.png,.jpg,.jpeg'

export function validateUploadFile(file: File): string | null {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!ext || !UPLOAD_LIMITS.allowedExtensions.includes(ext as typeof UPLOAD_LIMITS.allowedExtensions[number])) {
    return '暂不支持该格式'
  }
  if (file.size > UPLOAD_LIMITS.maxFileSizeMb * 1024 * 1024) {
    return `文件超过 ${UPLOAD_LIMITS.maxFileSizeMb}MB`
  }
  return null
}

export function parseTagsInput(input: string): string[] {
  return input
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
}

/** 规范化标签：去空白、去重、限制长度 */
export function normalizeTags(tags: string[]): string[] {
  const seen = new Set<string>()
  const result: string[] = []
  for (const raw of tags) {
    for (const part of parseTagsInput(raw)) {
      const tag = part.slice(0, 64)
      if (tag && !seen.has(tag)) {
        seen.add(tag)
        result.push(tag)
      }
    }
  }
  return result
}

/** 校验标签列表，失败返回错误文案 */
export function validateTags(tags: string[]): string | null {
  if (tags.length > 20) {
    return '单个文档最多 20 个标签'
  }
  for (const tag of tags) {
    if (!tag.trim()) {
      return '标签不能为空'
    }
    if (tag.length > 64) {
      return '单个标签不能超过 64 个字符'
    }
  }
  return null
}
