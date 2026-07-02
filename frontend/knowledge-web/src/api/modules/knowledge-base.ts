import request from '@/api/request'
import type { Envelope } from '@/api/request'

/**
 * F2 知识库管理 API 模块（commit #30 邱子悦前端整合）。
 *
 * <p>对齐：
 * <ul>
 *   <li>R20：复用主项目 request.ts（同域 /api/v1、access_token 注入）
 *   <li>R12：保留邱子悦原版 7 个端点；URL 改为主项目规范
 *   <li>R15：路径以 /admin/knowledge-bases 为前缀
 * </ul>
 *
 * <p>注意：admin KnowledgeBaseController 用 ApiResponse 包装（与 ConfigController 不同），
 * 沿用 F2 v1.0 文档默认约定。前端调用后需 .then((r) => r.data) 取真实数据。
 */
export interface KnowledgeBaseVO {
  id: number
  name: string
  description?: string
  category: string
  retrievalStrategy: string
  chunkStrategy: string
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  createdByUserId?: string
  createdByName?: string
  strategyVersion: number
  isDeleted?: number
  createdAt: string
  updatedAt?: string
  _isDeleted?: number
}

export interface KnowledgeBaseDetailVO extends KnowledgeBaseVO {
  separators: string[]
  separatorsJson: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

export interface CreateKnowledgeBaseRequest {
  name: string
  description?: string
  category: string
  retrievalStrategy: string
  chunkStrategy?: string
  chunkSize?: number
  chunkOverlap?: number
  separatorsJson?: string
}

export interface UpdateKnowledgeBaseRequest {
  name?: string
  description?: string
  category?: string
  retrievalStrategy?: string
  chunkStrategy?: string
  chunkSize?: number
  chunkOverlap?: number
  separatorsJson?: string
  confirmation?: boolean
}

export interface ReprocessResultVO {
  knowledgeBaseId: number
  taskId?: number | null
  readyDocumentCount: number
  taskCount?: number
  strategyVersion?: number
  message: string
  triggeredAt: string
}

/** 列表查询（分页） */
export function listKnowledgeBases(params: {
  category?: string
  nameKeyword?: string
  isDeleted?: number
  pageNum?: number
  pageSize?: number
}): Promise<Envelope<PageResult<KnowledgeBaseVO>>> {
  return request.get('/knowledge-bases', { params })
}

/** 详情 */
export function getKnowledgeBaseDetail(id: number): Promise<Envelope<KnowledgeBaseDetailVO>> {
  return request.get(`/knowledge-bases/${id}`)
}

/** 创建 */
export function createKnowledgeBase(
  payload: CreateKnowledgeBaseRequest,
): Promise<Envelope<KnowledgeBaseVO>> {
  return request.post('/knowledge-bases', payload)
}

/** 更新 */
export function updateKnowledgeBase(
  id: number,
  payload: UpdateKnowledgeBaseRequest,
  confirmation?: boolean,
): Promise<Envelope<KnowledgeBaseVO>> {
  const headers: Record<string, string> = {}
  if (confirmation !== undefined) headers['X-Confirmation'] = String(confirmation)
  return request.put(`/knowledge-bases/${id}`, payload, { headers })
}

/** 单个删除 */
export function deleteKnowledgeBase(id: number): Promise<Envelope<null>> {
  return request.delete(`/knowledge-bases/${id}`)
}

/** 批量删除 */
export function batchDeleteKnowledgeBases(
  knowledgeBaseIds: number[],
): Promise<Envelope<null>> {
  return request.post('/knowledge-bases/batch-delete', { knowledgeBaseIds })
}

/** 策略变更（reprocess） */
export function reprocessKnowledgeBase(
  id: number,
): Promise<Envelope<ReprocessResultVO>> {
  return request.post(`/knowledge-bases/${id}/reprocess`)
}
