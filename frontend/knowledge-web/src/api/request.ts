import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'

/**
 * 知识管理前端统一 Axios 封装。
 *
 * <p>行为约定：
 * <ul>
 *   <li>baseURL = '/api/v1'（同域，由 Nginx 反代到 km-admin-service）</li>
 *   <li>每个请求自动从 localStorage.access_token 取 Bearer Token</li>
 *   <li>响应统一解包 {code, message, data}：拦截器返回 resp.data.data</li>
 *   <li>401 跳回根路径（super-biz-agent 的登录页）</li>
 * </ul>
 */
const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

request.interceptors.request.use((config: AxiosRequestConfig) => {
  if (typeof window !== 'undefined') {
    const token = window.localStorage.getItem('access_token')
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
  }
  return config
})

request.interceptors.response.use(
  (resp: AxiosResponse) => resp.data,
  (error) => {
    if (error.response?.status === 401) {
      // Token 失效或缺失，跳回登录
      if (typeof window !== 'undefined') {
        window.location.href = '/'
      }
    }
    return Promise.reject(error)
  },
)

export interface Envelope<T> {
  code: number
  message: string
  data: T
}

export const get = <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<Envelope<T>> =>
  request.get<Envelope<T>>(url, config).then((r) => r as unknown as Envelope<T>)

export const post = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Envelope<T>> =>
  request.post<Envelope<T>>(url, data, config).then((r) => r as unknown as Envelope<T>)

export const put = <T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<Envelope<T>> =>
  request.put<Envelope<T>>(url, data, config).then((r) => r as unknown as Envelope<T>)

export const del = <T = unknown>(url: string, config?: AxiosRequestConfig): Promise<Envelope<T>> =>
  request.delete<Envelope<T>>(url, config).then((r) => r as unknown as Envelope<T>)

export default request
