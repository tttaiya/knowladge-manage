export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

async function parseResponse<T>(response: Response): Promise<T> {
  const json = (await response.json()) as ApiResult<T>
  if (json.code !== 0) {
    throw new Error(json.message)
  }
  return json.data
}

export async function request<T>(url: string, options?: RequestInit): Promise<T> {
  try {
    const response = await fetch(url, {
      headers: {
        'Content-Type': 'application/json'
      },
      ...options
    })
    return parseResponse<T>(response)
  } catch (error) {
    if (error instanceof Error && error.message === 'Failed to fetch') {
      throw new Error('请求后端失败，请确认后端服务已启动，且前端已通过 Vite 代理访问接口。')
    }
    throw error
  }
}