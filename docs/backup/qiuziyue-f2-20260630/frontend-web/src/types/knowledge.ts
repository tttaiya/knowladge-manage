export interface KnowledgeBaseQuery {
  q?: string
  category?: string
  page: number
  pageSize: number
}

export interface KnowledgeBaseCreateDTO {
  name: string
  description?: string
  category: string
  retrievalStrategy: string
  chunkStrategy: string
  chunkSize: number
  chunkOverlap: number
  separatorsJson?: string
}

export interface KnowledgeBaseUpdateDTO extends KnowledgeBaseCreateDTO {}

export interface KnowledgeBaseVO {
  id: number
  name: string
  description?: string
  category: string
  retrievalStrategy: string
  chunkStrategy: string
  chunkSize: number
  chunkOverlap: number
  separatorsJson?: string
  documentCount: number
  createdByUserId?: number
  createdByName?: string
  createdAt?: string
  updatedAt?: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

export interface UpdateKnowledgeBaseResultVO {
  id: number
  strategyChanged: boolean
  reprocessTriggered: boolean
  readyDocumentCount: number
}

export interface ReprocessResultVO {
  kbId: number
  readyDocumentCount: number
  message: string
}
