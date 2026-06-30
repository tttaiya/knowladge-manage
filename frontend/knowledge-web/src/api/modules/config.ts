import request from '@/api/request'

/**
 * F6 系统配置 API 模块（commit #19 钱小晓前端整合）。
 *
 * R20：使用现有 request.ts（同域 /api/v1、access_token 注入）
 * R19：业务逻辑 100% 保留钱小晓 ConfigPage 设计，api 层适配现有项目
 *
 * 注意：admin ConfigController 直接返回 DTO（不包装 ApiResponse，v6 修正），
 * 所以这里 .then((r) => r as T) 直接透传 request 返回值。
 */

export interface EmbeddingConfig {
  model: string
  apiBase: string
  apiKey?: string
  dimension: number
}

export interface RerankConfig {
  model: string
  apiBase: string
  apiKey?: string
  topN: number
  threshold: number
}

export interface ParserConfig {
  paddleocrEnabled: boolean
  maxConcurrentTasks: number
  maxRetryCount: number
  timeoutSeconds: number
}

export interface ConnectionTestPayload {
  type: 'embedding' | 'rerank' | 'ocr'
  apiBase: string
  apiKey?: string
}

export interface ConnectionTestResult {
  success: boolean
  message: string
  latencyMs?: number
}

/**
 * R6/v6：钱小晓的 apiKey 默认传 "********" 表示不修改；
 * 后端识别后保留数据库原值。空字符串视为清空。
 */
function stripMaskedApiKey<T extends { apiKey?: string }>(payload: T): T {
  const data = { ...payload }
  if (!data.apiKey || data.apiKey === '********') {
    delete data.apiKey
  }
  return data
}

export function getEmbeddingConfig() {
  return request.get<EmbeddingConfig>('/configs/embedding').then((r) => r.data)
}

export function updateEmbeddingConfig(data: EmbeddingConfig) {
  return request.put<EmbeddingConfig>('/configs/embedding', stripMaskedApiKey(data)).then((r) => r.data)
}

export function getRerankConfig() {
  return request.get<RerankConfig>('/configs/rerank').then((r) => r.data)
}

export function updateRerankConfig(data: RerankConfig) {
  return request.put<RerankConfig>('/configs/rerank', stripMaskedApiKey(data)).then((r) => r.data)
}

export function getParserConfig() {
  return request.get<ParserConfig>('/configs/parser').then((r) => r.data)
}

export function updateParserConfig(data: ParserConfig) {
  return request.put<ParserConfig>('/configs/parser', data).then((r) => r.data)
}

export function testConfigConnection(data: ConnectionTestPayload) {
  return request.post<ConnectionTestResult>('/configs/test-connection', stripMaskedApiKey(data)).then((r) => r.data)
}