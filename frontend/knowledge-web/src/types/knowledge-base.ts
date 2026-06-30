/**
 * F2 知识库管理类型定义（commit #30 邱子悦前端整合）。
 *
 * <p>与后端枚举值保持 1:1：
 * <ul>
 *   <li>category: REGULATION | REPORT_PAPER | TERM | GENERAL
 *   <li>retrievalStrategy: VECTOR_RERANK | SEMANTIC
 *   <li>chunkStrategy: HEADING | FIXED
 * </ul>
 */

export const KB_CATEGORIES = [
  { value: 'REGULATION', label: '规章制度' },
  { value: 'REPORT_PAPER', label: '报告/论文' },
  { value: 'TERM', label: '术语词条' },
  { value: 'GENERAL', label: '通用' },
] as const

export const KB_RETRIEVAL_STRATEGIES = [
  { value: 'VECTOR_RERANK', label: '向量+重排' },
  { value: 'SEMANTIC', label: '纯语义' },
] as const

export const KB_CHUNK_STRATEGIES = [
  { value: 'HEADING', label: '按标题切分' },
  { value: 'FIXED', label: '按固定长度' },
] as const

export type KBCategory = (typeof KB_CATEGORIES)[number]['value']
export type KBRetrievalStrategy = (typeof KB_RETRIEVAL_STRATEGIES)[number]['value']
export type KBChunkStrategy = (typeof KB_CHUNK_STRATEGIES)[number]['value']

export interface KnowledgeBaseListItem {
  id: number
  name: string
  description?: string
  category: string
  retrievalStrategy: string
  chunkStrategy: string
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  strategyVersion: number
  createdByName?: string
  createdAt: string
  updatedAt?: string
}

export interface KnowledgeBaseDetail extends KnowledgeBaseListItem {
  separators: string[]
  separatorsJson: string
  createdByUserId?: string
}
