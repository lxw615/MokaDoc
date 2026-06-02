/**
 * SSE (Server-Sent Events) 流式请求工具
 * 用于处理后端返回的 Flux<String> 流式响应
 */

interface SSEOptions {
  onMessage: (data: string) => void
  onError?: (error: Error) => void
  onComplete?: () => void
  signal?: AbortSignal
}

interface SSEFetchOptions extends Omit<SSEOptions, 'onMessage'> {
  onMessage?: (data: string) => void
  method?: 'GET' | 'POST'
  body?: any
  headers?: Record<string, string>
}

/**
 * 使用 EventSource 进行 SSE 流式请求
 * @param url - 请求URL
 * @param params - 查询参数
 * @param options - 回调选项
 */
export function useSSE(
  url: string,
  params: Record<string, string>,
  options: SSEOptions
): void {
  const { onMessage, onError, onComplete, signal } = options

  // 构建完整的URL带查询参数
  const queryString = new URLSearchParams(params).toString()
  const fullUrl = `${url}?${queryString}`

  // 创建 EventSource 连接
  const eventSource = new EventSource(fullUrl)

  // 监听消息事件
  eventSource.onmessage = (event) => {
    try {
      const data = event.data
      if (data && data !== '[DONE]') {
        onMessage(data)
      }
    } catch (error) {
      console.error('SSE message parsing error:', error)
      onError?.(error as Error)
    }
  }

  // 监听错误事件
  eventSource.onerror = (error) => {
    console.error('SSE connection error:', error)
    
    // 检查是否被主动中断
    if (signal?.aborted) {
      eventSource.close()
      return
    }

    // 判断连接状态
    if (eventSource.readyState === EventSource.CLOSED) {
      // 正常关闭
      eventSource.close()
      onComplete?.()
    } else {
      // 发生错误
      onError?.(new Error('SSE connection failed'))
      eventSource.close()
    }
  }

  // 监听中断信号
  if (signal) {
    signal.addEventListener('abort', () => {
      eventSource.close()
      onComplete?.()
    })
  }
}

/**
 * 使用 fetch API 进行 SSE 流式请求（备选方案）
 * 适用于需要自定义 headers 或 POST 请求的场景
 * @param url - 请求URL
 * @param paramsOrBody - GET请求时为查询参数对象，POST请求时为请求体对象
 * @param options - 回调选项和请求配置
 */
export async function useSSEFetch(
  url: string,
  paramsOrBody: Record<string, any>,
  options: SSEFetchOptions = {}
): Promise<void> {
  const { 
    onMessage = () => {}, 
    onError, 
    onComplete, 
    signal,
    method = 'GET',
    body,
    headers: customHeaders = {}
  } = options

  try {
    let fullUrl = url
    let requestBody: BodyInit | null | undefined = undefined
    
    // 构建请求配置
    const requestConfig: RequestInit = {
      method,
      headers: {
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        ...customHeaders,
      },
      signal,
    }

    if (method === 'GET') {
      // GET 请求：将参数作为 query string
      const queryString = new URLSearchParams(paramsOrBody).toString()
      fullUrl = `${url}?${queryString}`
    } else if (method === 'POST') {
      // POST 请求：将参数作为 JSON body
      requestConfig.headers = {
        ...requestConfig.headers,
        'Content-Type': 'application/json',
      }
      requestBody = JSON.stringify(body || paramsOrBody)
      requestConfig.body = requestBody
    }

    const response = await fetch(fullUrl, requestConfig)

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    if (!response.body) {
      throw new Error('Response body is null')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()

      if (done) {
        break
      }

      // 解码数据块
      buffer += decoder.decode(value, { stream: true })

      // 按行分割
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 保留最后一个不完整的行

      for (const line of lines) {
        const trimmedLine = line.trim()

        // 跳过空行和注释
        if (!trimmedLine || trimmedLine.startsWith(':')) {
          continue
        }

        // 解析 SSE 格式: data: xxx
        if (trimmedLine.startsWith('data:')) {
          const data = trimmedLine.substring(5).trim()

          if (data === '[DONE]') {
            // 流结束标记
            reader.cancel()
            onComplete?.()
            return
          }

          if (data) {
            onMessage(data)
          }
        }
      }
    }

    // 处理剩余的缓冲区
    if (buffer.trim()) {
      const trimmedBuffer = buffer.trim()
      if (trimmedBuffer.startsWith('data:')) {
        const data = trimmedBuffer.substring(5).trim()
        if (data && data !== '[DONE]') {
          onMessage(data)
        }
      }
    }

    onComplete?.()
  } catch (error) {
    // 如果是主动中断，不触发错误回调
    if (error instanceof DOMException && error.name === 'AbortError') {
      onComplete?.()
      return
    }

    console.error('SSE fetch error:', error)
    onError?.(error as Error)
  }
}

/**
 * 创建 AbortController 用于中断 SSE 连接
 * @returns AbortController 实例
 */
export function createAbortController(): AbortController {
  return new AbortController()
}
