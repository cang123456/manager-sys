import { getToken } from '@/utils/auth'

/**
 * 调用后端 AI 助手（LangChain4j 版）
 *
 * 协议比手写版简单得多：
 *   请求只发 { sessionId, message }，记忆与工具定义都在后端
 *   响应事件只有四种：
 *     chunk  {text}                                              文字增量
 *     tool   {model, requestId, toolCalls:[{id,name,arguments}]}  工具调用（参数已由框架拼好）
 *     done   {}                                                  正常结束
 *     error  {message}                                           出错
 */
export async function chatStream({ sessionId, message, onText, onTool, onDone, onError, signal }) {
  const base = import.meta.env.VITE_APP_BASE_API || ''
  let response
  try {
    response = await fetch(`${base}/ai/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json;charset=utf-8',
        'Authorization': 'Bearer ' + getToken()
      },
      body: JSON.stringify({ sessionId, message }),
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
  // 若依鉴权失败返回 HTTP 200 + JSON，不是 SSE
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const text = await response.text()
    let msg = '请求被拒绝'
    try {
      const body = JSON.parse(text)
      msg = body.msg || msg
    } catch (e) {
      msg = text || msg
    }
    throw new Error(msg)
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
      let payload
      try {
        payload = JSON.parse(data)
      } catch (e) {
        continue
      }
      if (event === 'chunk') {
        onText && onText(payload.text || '')
      } else if (event === 'tool') {
        onTool && onTool(payload)
      } else if (event === 'done') {
        onDone && onDone()
      } else if (event === 'error') {
        onError && onError(new Error(payload.message || '调用失败'))
      }
    }
  }
}

/** 把前端执行工具的结果回报给后端，写入该会话记忆 */
export async function postToolResult({ sessionId, toolCallId, toolName, result }) {
  const base = import.meta.env.VITE_APP_BASE_API || ''
  await fetch(`${base}/ai/tool-result`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      'Authorization': 'Bearer ' + getToken()
    },
    body: JSON.stringify({ sessionId, toolCallId, toolName, result })
  })
}

/** 清空后端记忆 */
export async function resetSession(sessionId) {
  const base = import.meta.env.VITE_APP_BASE_API || ''
  await fetch(`${base}/ai/reset`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json;charset=utf-8',
      'Authorization': 'Bearer ' + getToken()
    },
    body: JSON.stringify({ sessionId })
  })
}

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
