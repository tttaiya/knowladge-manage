import { get, patch, post } from '@/api/request'
import type { PageResult } from '@/types/knowledge'

export interface PendingReviewDocument {
  docId: number
  kbId: number
  kbName?: string
  originalName: string
  status: string
  chunkCount?: number
  uploaderName?: string
  createdAt?: string
}

export interface ReviewChunk {
  chunkId: number
  chunkIndex: number
  chapterPath?: string
  pageNo?: number
  chunkType?: string
  content: string
  charCount?: number
  vectorId?: string
  vectorStatus?: string
  isEdited?: number
}

export interface ReviewDocumentDetail {
  docId: number
  kbId: number
  kbName?: string
  originalName: string
  status: string
  tags?: string[]
  chunkCount?: number
  chunks: ReviewChunk[]
}

function unwrap<T>(promise: Promise<{ code: number; message: string; data: T }>) {
  return promise.then((res) => {
    if (res.code !== 0) {
      throw new Error(res.message || '请求失败')
    }
    return res.data
  })
}

export function fetchPendingReviewDocuments(params: { kbId?: number; page?: number; pageSize?: number }) {
  return unwrap(get<PageResult<PendingReviewDocument>>('/reviews/pending-documents', { params }))
}

export function fetchReviewDocumentDetail(docId: number) {
  return unwrap(get<ReviewDocumentDetail>(`/reviews/documents/${docId}`))
}

export function approveReviewDocument(docId: number, comment?: string) {
  return unwrap(post<number>(`/reviews/documents/${docId}/approve`, { comment }))
}

export function rejectReviewDocument(docId: number, reason: string) {
  return unwrap(post<number>(`/reviews/documents/${docId}/reject`, { reason }))
}

export function updateReviewChunk(chunkId: number, content: string) {
  return unwrap(patch<number>(`/reviews/chunks/${chunkId}`, { content }))
}
