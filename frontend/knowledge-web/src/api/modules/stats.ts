import { get } from '@/api/request'

/**
 * F8 数据统计 API 模块。
 *
 * <p>对齐：
 * <ul>
 *   <li>GET /api/v1/stats/overview?days=N（KmAdminService StatsController）</li>
 *   <li>字段 camelCase 与后端 StatsOverviewDTO 一一对应</li>
 *   <li>R20：复用主项目 request.ts（同域 /api/v1、access_token 注入）</li>
 * </ul>
 *
 * <p>调用方需自行解包：`get` helper 在 request.ts 内部返回 envelope，调用方
 * 用 `unwrap` 检查 `code === 0` 后取 `data`。
 */

/** 趋势数据点：单日 {date, count}（与后端 TrendDataDTO 一致） */
export interface TrendData {
  date: string
  count: number
}

/** 统计概览响应数据（与后端 StatsOverviewDTO 一致） */
export interface StatsOverview {
  knowledgeBaseTotal: number
  documentTotal: number
  chunkTotal: number
  documentReady: number
  documentPendingReview: number
  documentFailed: number
  taskProcessing: number
  documentTrend: TrendData[]
}

/**
 * 标准 envelope（与 request.ts 中的 Envelope 等价，但此处显式声明，
 * 避免调用方反向依赖 request 内部类型）。
 */
export interface StatsEnvelope<T> {
  code: number
  message: string
  data: T
}

/**
 * 解包 envelope：code !== 0 时抛出后端 message。
 * 与 review.ts 中的 unwrap 行为一致。
 */
function unwrap<T>(promise: Promise<StatsEnvelope<T>>): Promise<T> {
  return promise.then((res) => {
    if (res.code !== 0) {
      throw new Error(res.message || '请求失败')
    }
    return res.data
  })
}

/**
 * 获取统计概览。
 *
 * @param days 趋势天数（默认 30；后端限定 [1, 365]，越界返回 400/1001）
 * @returns 解包后的 StatsOverview
 */
export function fetchStatsOverview(days = 30): Promise<StatsOverview> {
  return unwrap(get<StatsOverview>('/stats/overview', { params: { days } }))
}