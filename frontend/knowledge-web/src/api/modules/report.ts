import { del, get, post, put } from '@/api/request'
import type { Envelope } from '@/api/request'

type AnyRecord = Record<string, unknown>

function cleanObject<T extends AnyRecord>(data: T): Partial<T> {
  return Object.entries(data).reduce<Partial<T>>((result, [key, value]) => {
    if (value !== undefined) {
      result[key as keyof T] = value as T[keyof T]
    }
    return result
  }, {})
}

function cleanGarbageText<T>(value: T): T {
  if (typeof value === 'string') {
    return value.replace(/\[AI[^\]]*\]/g, '') as T
  }
  if (Array.isArray(value)) {
    return value.map(cleanGarbageText) as T
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as AnyRecord).map(([key, item]) => [key, cleanGarbageText(item)]),
    ) as T
  }
  return value
}

async function unwrap<T>(promise: Promise<Envelope<T>>): Promise<T> {
  const payload = await promise
  if (!payload) {
    return null as T
  }
  if (payload.code !== 0 && payload.code !== 200) {
    throw new Error(payload.message || `接口返回失败：${payload.code}`)
  }
  return cleanGarbageText(payload.data)
}

function normalizeOutlineItem(item: AnyRecord): AnyRecord {
  return cleanObject({
    id: item.id,
    reportId: item.reportId,
    parentId: item.parentId ?? 0,
    chapterNo: item.chapterNo,
    chapterTitle: item.chapterTitle,
    level: item.level ?? 1,
    sort: item.sort ?? 0,
    editable: item.editable ?? 1,
    aiGenerated: item.aiGenerated ?? 0,
    status: item.status || 'DRAFT',
    remark: item.remark,
    generationPrompt: item.generationPrompt,
  })
}

export const reportApi = {
  health() {
    return unwrap(get('/reports/health'))
  },
  dashboardOverview() {
    return unwrap(get('/reports/dashboard/overview'))
  },
  dashboardTrend() {
    return unwrap(get('/reports/dashboard/trends/last30days'))
  },
  listTemplates() {
    return unwrap(get('/reports/templates'))
  },
  createTemplate(data: AnyRecord) {
    return unwrap(post('/reports/templates', cleanObject(data)))
  },
  uploadTemplateFile(templateId: number | string, file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return unwrap(post(`/reports/templates/${templateId}/upload`, formData))
  },
  saveTemplateChapters(templateId: number | string, chapters: unknown[]) {
    return unwrap(post('/reports/templates/chapters', { templateId, chapters }))
  },
  getTemplateChapters(templateId: number | string) {
    return unwrap(get(`/reports/templates/${templateId}/chapters`))
  },
  updateTemplate(id: number | string, data: AnyRecord) {
    return unwrap(put(`/reports/templates/${id}`, cleanObject(data)))
  },
  deleteTemplate(id: number | string) {
    return unwrap(del(`/reports/templates/${id}`))
  },
  listRecords() {
    return unwrap(get('/reports/records'))
  },
  getRecord(id: number | string) {
    return unwrap(get(`/reports/records/${id}`))
  },
  deleteRecord(id: number | string) {
    return unwrap(del(`/reports/records/${id}`))
  },
  createDraft(data: AnyRecord) {
    return unwrap(post('/reports/outlines/draft', cleanObject(data)))
  },
  getOutline(reportId: number | string) {
    return unwrap(get(`/reports/outlines/${reportId}`))
  },
  updateOutline(reportId: number | string, items: AnyRecord[]) {
    return unwrap(put(`/reports/outlines/${reportId}`, items.map(normalizeOutlineItem)))
  },
  addOutlineItem(reportId: number | string, item: AnyRecord) {
    return unwrap(post(`/reports/outlines/${reportId}/items`, normalizeOutlineItem(item)))
  },
  updateOutlineItem(itemId: number | string, item: AnyRecord) {
    return unwrap(put(`/reports/outlines/items/${itemId}`, normalizeOutlineItem(item)))
  },
  deleteOutlineItem(itemId: number | string) {
    return unwrap(del(`/reports/outlines/items/${itemId}`))
  },
  regenerateOutline(reportId: number | string) {
    return unwrap(post(`/reports/outlines/${reportId}/regenerate`))
  },
  moveOutlineItem(reportId: number | string, itemId: number | string, item: AnyRecord) {
    return unwrap(post(`/reports/outlines/${reportId}/items/${itemId}/move`, normalizeOutlineItem(item)))
  },
  startGeneration(reportId: number | string, data: AnyRecord = {}) {
    return unwrap(post('/reports/generation', {
      reportId: Number(reportId),
      stream: false,
      templateId: data.templateId || undefined,
      generationPrompt: data.generationPrompt || undefined,
    }))
  },
  getProgress(reportId: number | string) {
    return unwrap(get(`/reports/generation/${reportId}/progress`))
  },
  listChapters(reportId: number | string) {
    return unwrap(get(`/reports/chapters/report/${reportId}`))
  },
  getChapter(chapterId: number | string) {
    return unwrap(get(`/reports/chapters/${chapterId}`))
  },
  saveChapter(chapterId: number | string, data: AnyRecord) {
    return unwrap(put(`/reports/chapters/${chapterId}`, cleanObject(data)))
  },
  regenerateChapter(chapterId: number | string, data: AnyRecord = {}) {
    return unwrap(post(`/reports/chapters/${chapterId}/ai-regenerate`, cleanObject(data)))
  },
  insertTable(chapterId: number | string, data: AnyRecord = {}) {
    return unwrap(post(`/reports/chapters/${chapterId}/table`, cleanObject({
      title: data.title,
      headers: data.headers,
      rows: data.rows,
    })))
  },
  insertImage(chapterId: number | string, data: AnyRecord = {}) {
    return unwrap(post(`/reports/chapters/${chapterId}/image`, cleanObject({
      imageUrl: data.imageUrl,
      title: data.title,
    })))
  },
  deleteChapter(chapterId: number | string) {
    return unwrap(del(`/reports/chapters/${chapterId}`))
  },
  listChapterReferences(chapterId: number | string) {
    return unwrap(get(`/reports/chapters/${chapterId}/references`))
  },
  regenerateDocx(reportId: number | string) {
    return unwrap(get(`/reports/records/${reportId}/export/docx/regenerate`))
  },
  uploadImage(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return unwrap(post('/reports/materials/images', formData))
  },
}

export function fileDownloadUrl(fileNameOrUrl: string): string {
  if (!fileNameOrUrl) return ''
  if (fileNameOrUrl.startsWith('http') || fileNameOrUrl.startsWith('/api')) {
    return fileNameOrUrl
  }
  return `/api/v1/reports/files/${encodeURIComponent(fileNameOrUrl)}`
}
