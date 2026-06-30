import { request } from '../request'
import type {
  KnowledgeBaseCreateDTO,
  KnowledgeBaseQuery,
  KnowledgeBaseUpdateDTO,
  KnowledgeBaseVO,
  PageResult,
  ReprocessResultVO,
  UpdateKnowledgeBaseResultVO
} from '../../types/knowledge'

const BASE_URL = '/api/v1/knowledge-bases'

export async function listKnowledgeBases(params: KnowledgeBaseQuery): Promise<PageResult<KnowledgeBaseVO>> {
  const query = new URLSearchParams()
  if (params.q) query.append('q', params.q)
  if (params.category) query.append('category', params.category)
  query.append('page', String(params.page))
  query.append('pageSize', String(params.pageSize))
  return request<PageResult<KnowledgeBaseVO>>(BASE_URL + '?' + query.toString())
}

export async function getKnowledgeBase(id: number): Promise<KnowledgeBaseVO> {
  return request<KnowledgeBaseVO>(BASE_URL + '/' + id)
}

export async function createKnowledgeBase(dto: KnowledgeBaseCreateDTO): Promise<{ id: number }> {
  return request<{ id: number }>(BASE_URL, {
    method: 'POST',
    body: JSON.stringify(dto)
  })
}

export async function updateKnowledgeBase(id: number, dto: KnowledgeBaseUpdateDTO, confirmation?: boolean): Promise<UpdateKnowledgeBaseResultVO> {
  const url = confirmation ? BASE_URL + '/' + id + '?confirmation=true' : BASE_URL + '/' + id
  return request<UpdateKnowledgeBaseResultVO>(url, {
    method: 'PUT',
    body: JSON.stringify(dto)
  })
}

export async function deleteKnowledgeBase(id: number): Promise<void> {
  await request<void>(BASE_URL + '/' + id, { method: 'DELETE' })
}

export async function batchDeleteKnowledgeBases(ids: number[]): Promise<{ affectedRows: number }> {
  return request<{ affectedRows: number }>(BASE_URL + '/batch-delete', {
    method: 'POST',
    body: JSON.stringify({ ids })
  })
}

export async function reprocessKnowledgeBase(id: number): Promise<ReprocessResultVO> {
  return request<ReprocessResultVO>(BASE_URL + '/' + id + '/reprocess?confirmation=true', {
    method: 'POST'
  })
}