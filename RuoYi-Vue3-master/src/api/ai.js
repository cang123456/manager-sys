import { getToken } from '@/utils/auth'

/**
 * 调用后端 AI 代理，读取 SSE 流
 *
 * 后端只做透明转发，每个分片就是百炼原始 chunk 的 JSON 字符串，
 * 这里解析出来直接交给上层，不在这一层做任何业务解释。
 *
 * @param {Object}   options
 * @param {Array}    options.messages 对话历史 [{role, content}]
 * @param {Array}    options.tools    工具定义（OpenAI function 格式）
 * @param {Function} options.onChunk  收到一个分片对象时回调
 * @param {Function} options.onDone   正常结束时回调
 * @param {Function} options.onError  出错时回调
 * @param {AbortSignal} options.signal 用于中止
 */
export async function chatStream({ messages, tools, onChunk, onDone, onError, signal }) {
  const base = import.meta.env.VITE_APP_BASE_API || ''
  let response
  try {
    response = await fetch(`${base}/ai/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=utf-8',
        'Authorization': 'Bearer ' + getToken()
      },
      body: JSON.stringify({ messages, tools }),
      signal
    })
  } catch (e) {
    if (e.name === 'AbortError') {
      return
    }
    throw new Error('无法连接后端服务：' + e.message)
  }

  if (response.status === 401) {
    throw new Error('登录状态已过期，请重新登录后再试')
  }
  if (!response.ok) {
    throw new Error(`后端返回 HTTP ${response.status}`)
  }
  // 若依的惯例是 HTTP 200 + 业务码，鉴权失败时返回的是 JSON 而不是 SSE 流
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const text = await response.text()
    let message = '请求被拒绝'
    try {
      const body = JSON.parse(text)
      message = body.msg || message
    } catch (e) {
      message = text || message
    }
    throw new Error(message)
  }
  if (!response.body) {
    throw new Error('当前浏览器不支持流式读取')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary
    while ((boundary = buffer.indexOf('\n\n')) >= 0) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const { event, data } = parseEvent(rawEvent)
      if (!data) {
        continue
      }
      if (event === 'chunk') {
        try {
          onChunk && onChunk(JSON.parse(data))
        } catch (e) {
          console.warn('[ai] 分片解析失败', data, e)
        }
      } else if (event === 'done') {
        onDone && onDone()
      } else if (event === 'error') {
        let message = '调用失败'
        try {
          message = JSON.parse(data).message || message
        } catch (e) {
          message = data
        }
        onError && onError(new Error(message))
      }
    }
  }
}

/** 解析一段 SSE 文本，返回 { event, data } */
function parseEvent(raw) {
  let event = 'message'
  const dataLines = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  return { event, data: dataLines.join('\n') }
}
