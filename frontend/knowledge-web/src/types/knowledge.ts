/**
 * 知识管理模块统一 TypeScript 类型。
 * R14：uploaderUserId 是 String UUID。
 * R12：originalName 字段对应 SQL file_name 列；filePath 对应 object_key 列；
 *      status 对应 document_status 列。
 */

export type DocumentStatus =
  | 'UPLOADED'
  | 'PARSING'
  | 'CHUNKING'
  | 'VECTORIZING'
  | 'PENDING_REVIEW'
  | 'READY'
  | 'REVIEW_REJECTED'
  | 'FAILED'

export interface DocumentItem {
  id: number
  kbId: number
  /** 来自 SQL file_name 列 */
  originalName: string
  /** 来自 SQL object_key 列 */
  filePath: string
  mimeType?: string
  fileSize?: number
  fileHash?: string
  extension?: string
  /** 来自 SQL document_status 列 */
  status: DocumentStatus | string
  errorStage?: string
  errorMessage?: string
  chunkCount?: number
  retryCount?: number
  /** R14：String UUID */
  uploaderUserId?: string
  uploaderName?: string
  isDeleted?: number
  deletedAt?: string
  createdAt?: string
  updatedAt?: string
  tags?: string[]
}

export interface DocumentChunk {
  id: number
  chunkIndex: number
  content: string
  charCount: number
  chapterPath?: string
  pageNo?: number
  chunkType?: string
  vectorId?: string
  vectorStatus?: string
  isEdited?: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
}
