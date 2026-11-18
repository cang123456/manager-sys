<template>
  <div class="ai-root">
    <!-- 悬浮球 -->
    <button v-show="!open" class="ai-ball" title="打开智能助手" @click="open = true">
      <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.7"
           stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 3l1.9 4.6L18.5 9.5 13.9 11.4 12 16l-1.9-4.6L5.5 9.5l4.6-1.9L12 3z" />
        <path d="M18 15.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8.8-2z" />
      </svg>
    </button>

    <!-- 抽屉 -->
    <transition name="ai-slide">
      <section v-if="open" class="ai-panel">
        <header class="ai-head">
          <div class="ai-brand">
            <span class="ai-logo">AI</span>
            <div class="ai-brand-text">
              <div class="ai-name">智能助手</div>
              <div class="ai-desc">{{ modelLabel }}</div>
            </div>
          </div>
          <button class="ai-new-btn" title="清空记忆，开始新对话" @click="newSession">新对话</button>
          <button class="ai-icon-btn" title="收起" @click="open = false">✕</button>
        </header>

        <div ref="bodyRef" class="ai-body">
          <div v-if="!messages.length" class="ai-intro">
            <p class="ai-intro-title">说一句话，我帮你跳页面</p>
            <div class="ai-chips">
              <button v-for="s in samples" :key="s" class="ai-chip" @click="send(s)">{{ s }}</button>
            </div>
            <p class="ai-intro-tip">
              页面上的工具调用来自模型真实返回，不是前端写死的关键词匹配 —— 点「查看原始 JSON」可以看到它。
            </p>
          </div>

          <div v-for="(m, i) in messages" :key="i" :class="['ai-row', m.role]">
            <div class="ai-bubble">
              <span v-if="m.text">{{ m.text }}</span>
              <span v-else-if="m.pending" class="ai-dots"><i /><i /><i /></span>
            </div>

            <div v-if="m.tool" class="ai-toolcard">
              <div class="ai-tool-top">
                <span class="ai-tool-name">{{ m.tool.name }}</span>
                <span class="ai-tool-args">{{ m.tool.argsText }}</span>
              </div>
              <button class="ai-link" @click="m.showJson = !m.showJson">
                {{ m.showJson ? '收起原始 JSON' : '查看原始 JSON' }}
              </button>
              <button v-if="m.result && !m.result.ok" class="ai-link" @click="retryTool(m)">
                再跳一次
              </button>
              <pre v-if="m.showJson" class="ai-json">{{ m.tool.raw }}</pre>
              <div v-if="m.result" :class="['ai-result', m.result.ok ? 'ok' : 'fail']">
                <div>{{ m.result.text }}</div>
                <pre v-if="m.result.debug" class="ai-json">{{ m.result.debug }}</pre>
              </div>
            </div>
          </div>
        </div>

        <footer class="ai-foot">
          <textarea
            ref="inputRef"
            v-model="input"
            class="ai-input"
            rows="1"
            placeholder="例如：打开用户管理"
            :disabled="loading"
            @keydown.enter.exact.prevent="send()"
            @input="autoGrow"
          />
          <button v-if="!loading" class="ai-send" :disabled="!input.trim()" @click="send()">发送</button>
          <button v-else class="ai-send is-stop" @click="stop">停止</button>
        </footer>
      </section>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { chatStream, postToolResult, resetSession } from '@/api/ai'
import { describeToolCall, executeTool, hasTool, bindRouter } from '@/utils/aiTools'

// 把应用实际在用的 router 交给工具层。
// 开发态 HMR 可能重新执行 router 模块，模块级 import 拿到的是没有动态路由的新实例，
// 会让助手误判"路由未注册"；useRouter() 取的是 app.use() 绑定的那个实例。
bindRouter(useRouter())

const open = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref([])
const bodyRef = ref(null)
const inputRef = ref(null)
const samples = ['打开用户管理', '去角色管理', '回到首页']

/** 实际使用的模型名：来自后端返回的分片，拿到之前不写死 */
const activeModel = ref('')
const modelLabel = computed(() => (activeModel.value ? `百炼 ${activeModel.value} · 菜单跳转` : '百炼 · 菜单跳转'))

/** 会话 id：存 localStorage，刷新页面后记忆仍然连续 */
const SESSION_KEY = 'ai-assistant-session-id'

function ensureSessionId() {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = 's-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8)
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}

/* ---------- 界面消息的本地缓存 ----------
 * 后端记忆在 Redis，但界面上那串气泡只存在本组件的内存里，F5 一刷新就空，
 * 看起来像"记忆丢了"——其实模型那边还记得。这里把 messages 同步到本地存储，
 * 刷新后恢复展示。
 *
 * 注意定位：它只是**展示层缓存**，不是记忆的真相源。真相在 Redis，
 * 清掉浏览器数据不会让模型失忆，只会让界面变空。
 */
function messagesKey(sessionId) {
  return 'ai-assistant-messages:' + sessionId
}

function loadMessages() {
  try {
    const raw = localStorage.getItem(messagesKey(ensureSessionId()))
    if (!raw) return []
    const list = JSON.parse(raw)
    if (!Array.isArray(list)) return []
    // 刷新时不可能还有请求在飞，pending 一律清掉，否则会一直转圈
    return list.map(m => ({ ...m, pending: false }))
  } catch (e) {
    return []
  }
}

function persistMessages() {
  try {
    localStorage.setItem(messagesKey(ensureSessionId()), JSON.stringify(messages.value))
  } catch (e) {
    // 超出容量就放弃缓存，不影响对话本身
  }
}

/** 打字机每 18ms 改一次 text，不能每次都写盘，节流 500ms */
let persistTimer = null
function schedulePersist() {
  if (persistTimer) return
  persistTimer = setTimeout(() => {
    persistTimer = null
    persistMessages()
  }, 500)
}

messages.value = loadMessages()
watch(messages, schedulePersist, { deep: true })

/** 开一个新会话：清空后端记忆 + 界面消息 */
async function newSession() {
  const old = localStorage.getItem(SESSION_KEY)
  if (old) {
    resetSession(old).catch(() => {})
    localStorage.removeItem(messagesKey(old))
  }
  localStorage.removeItem(SESSION_KEY)
  ensureSessionId()
  messages.value = []
}

let controller = null
let aborted = false

/* ---------- 逐字输出（打字机） ----------
 * 流式工具调用时模型不返回 content，所以这里同时承担两件事：
 * 1. 模型真的返回文字时，逐字吐出来
 * 2. 模型只返回工具调用时，用它补的那句文案逐字吐出来
 */
const typeStates = new WeakMap()

function typeInto(msg, text) {
  if (!text) return
  let state = typeStates.get(msg)
  if (!state) {
    state = { queue: [], timer: null }
    typeStates.set(msg, state)
  }
  state.queue.push(...String(text))
  if (state.timer) return
  state.timer = setInterval(() => {
    if (!state.queue.length) {
      clearInterval(state.timer)
      state.timer = null
      return
    }
    msg.text = (msg.text || '') + state.queue.shift()
    scrollToBottom()
  }, 18)
}

function scrollToBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

function autoGrow() {
  const el = inputRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 120) + 'px'
}

/* ---------- 发送 ---------- */
async function send(preset) {
  const content = String(preset ?? input.value).trim()
  if (!content || loading.value) return

  input.value = ''
  nextTick(autoGrow)

  messages.value.push({ role: 'user', text: content })
  messages.value.push({ role: 'assistant', text: '', pending: true, tool: null, result: null })
  const ai = messages.value[messages.value.length - 1]

  loading.value = true
  aborted = false
  controller = new AbortController()

  let toolEvent = null
  let streamError = null
  let gotText = false

  try {
    await chatStream({
      sessionId: ensureSessionId(),
      message: content,
      signal: controller.signal,
      onText(text) {
        if (!text) return
        gotText = true
        typeInto(ai, text)
      },
      onTool(payload) {
        toolEvent = payload
        if (payload.model) activeModel.value = payload.model
      },
      onError(err) {
        streamError = err
      }
    })
  } catch (err) {
    streamError = err
  } finally {
    loading.value = false
    ai.pending = false
  }

  if (aborted) {
    typeInto(ai, ai.text ? '（已停止）' : '已停止')
    return
  }

  if (streamError) {
    typeInto(ai, '出错了：' + streamError.message)
    return
  }

  /* ---------- 处理工具调用（参数已由 LangChain4j 拼好，前端不再累加分片） ---------- */
  const call = toolEvent && toolEvent.toolCalls && toolEvent.toolCalls[0]
  if (call) {
    let args = {}
    try {
      args = JSON.parse(call.arguments || '{}')
    } catch (e) {
      args = {}
    }

    ai.tool = {
      name: call.name,
      argsText: call.arguments || '(空)',
      id: call.id,
      showJson: false,
      raw: JSON.stringify(toolEvent, null, 2)
    }

    if (!args || !args.route) {
      typeInto(ai, '模型这次没给出具体页面（参数是空的）。请再说一次要打开哪个菜单，比如「打开角色管理」。')
      return
    }

    typeInto(ai, describeToolCall(call.name, args))

    const result = hasTool(call.name)
      ? await executeTool(call.name, args)
      : { ok: false, error: `未注册的工具：${call.name}` }

    ai.result = buildResult(result)

    // 把执行结果回报给后端，写进会话记忆（下一轮模型才知道已经跳过了）
    postToolResult({
      sessionId: ensureSessionId(),
      toolCallId: call.id,
      toolName: call.name,
      result: result.ok ? `已跳转到 ${result.route}` : `失败：${result.error}`
    }).catch(() => {})

    scrollToBottom()
    return
  }

  if (!gotText && !ai.text) {
    typeInto(ai, '这句话我没听明白。可以说「打开用户管理」这种指定页面的指令。')
  }
}

/** 统一整理执行结果：跳转前后路径没变也要明确说出来，不能假装成功 */
function buildResult(result, argsText) {
  if (!result.ok) {
    return { ok: false, text: '执行失败：' + result.error, debug: result.debug ? JSON.stringify(result.debug, null, 2) : '' }
  }
  const d = result.debug || {}
  const moved = d.跳转前 !== d.跳转后
  const debugText = Object.keys(d).length ? JSON.stringify(d, null, 2) : ''

  if (moved) {
    return { ok: true, text: `已跳转到 ${d.跳转后 || result.route}`, debug: debugText }
  }
  // 目标本来就是当前页面，属于正常情况，不该报失败
  if (d.目标路由 && d.跳转前 === d.目标路由) {
    return { ok: true, text: `你已经在 ${d.跳转前} 页面了`, debug: debugText }
  }
  return { ok: false, text: `跳转没有生效（${d.跳转前 || '?'} → ${d.跳转后 || '?'}）`, debug: debugText }
}

/** 手动重试：不经过模型，直接拿上次的参数再执行一次，用来区分是路由问题还是 AI 流程问题 */
async function retryTool(msg) {
  if (!msg.tool) {
    return
  }
  let args = {}
  try {
    args = JSON.parse(msg.tool.argsText || '{}')
  } catch (e) {
    args = {}
  }
  msg.result = { ok: false, text: '正在重试…', debug: '' }
  const result = await executeTool(msg.tool.name, args)
  msg.result = buildResult(result, msg.tool.argsText)
}

function stop() {
  aborted = true
  if (controller) controller.abort()
}

/* ---------- 原始 JSON 证据 ---------- */
function buildRawJson({ requestId, modelName, first, rawChunks }) {
  const preview = rawChunks.slice(0, 4).map(chunk => {
    const delta = chunk.choices && chunk.choices[0] ? chunk.choices[0].delta : {}
    return { delta: { content: delta.content, tool_calls: delta.tool_calls } }
  })
  const payload = {
    request_id: requestId,
    model: modelName,
    工具: { id: first.id, name: first.name },
    参数原始字符串: first.argsText,
    参数解析后: safeParse(first.argsText),
    分片总数: rawChunks.length,
    分片样本: preview
  }
  return JSON.stringify(payload, null, 2)
}

function safeParse(text) {
  try {
    return JSON.parse(text)
  } catch (e) {
    return '（无法解析，说明分片没拼完整）'
  }
}
</script>

<style scoped>
.ai-root {
  position: fixed;
  z-index: 2000;
  right: 0;
  bottom: 0;
  pointer-events: none;
}

/* ---------- 悬浮球 ---------- */
.ai-ball {
  pointer-events: auto;
  position: fixed;
  right: 26px;
  bottom: 26px;
  width: 54px;
  height: 54px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  color: #fff;
  background: linear-gradient(135deg, #0071e3 0%, #4a9dff 100%);
  box-shadow: 0 8px 24px rgba(0, 113, 227, 0.32), 0 2px 6px rgba(0, 0, 0, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform var(--ap-dur, 0.25s) var(--ap-ease, ease), box-shadow 0.25s ease;
}

.ai-ball:hover {
  transform: translateY(-2px) scale(1.04);
  box-shadow: 0 12px 30px rgba(0, 113, 227, 0.42), 0 3px 8px rgba(0, 0, 0, 0.16);
}

/* ---------- 抽屉 ---------- */
.ai-panel {
  pointer-events: auto;
  position: fixed;
  right: 24px;
  top: 78px;
  bottom: 24px;
  width: min(408px, calc(100vw - 32px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 18px;
  background: var(--ap-surface, #fff);
  border: 1px solid var(--ap-hairline, rgba(0, 0, 0, 0.08));
  box-shadow: 0 24px 64px rgba(0, 0, 0, 0.18), 0 4px 12px rgba(0, 0, 0, 0.06);
}

.ai-head {
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--ap-hairline, rgba(0, 0, 0, 0.08));
  background: var(--ap-surface-muted, #fbfbfd);
}

.ai-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-logo {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #0071e3, #4a9dff);
}

.ai-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ap-text, #1d1d1f);
  line-height: 1.3;
}

.ai-desc {
  font-size: 11px;
  color: var(--ap-text-tertiary, #86868b);
}

.ai-icon-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  background: transparent;
  color: var(--ap-text-secondary, #6e6e73);
  font-size: 13px;
}

.ai-new-btn {
  border: 1px solid var(--ap-hairline-strong, rgba(0, 0, 0, 0.14));
  background: transparent;
  color: var(--ap-text-secondary, #6e6e73);
  font-size: 11px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
  margin-right: 6px;
}

.ai-new-btn:hover {
  color: var(--ap-blue, #0071e3);
  border-color: var(--ap-blue, #0071e3);
}

.ai-icon-btn:hover {
  background: var(--ap-fill, rgba(0, 0, 0, 0.04));
}

/* ---------- 消息区 ---------- */
.ai-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: var(--ap-bg, #f5f5f7);
}

.ai-intro {
  margin: auto 0;
  text-align: center;
  padding: 8px 4px;
}

.ai-intro-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ap-text, #1d1d1f);
  margin: 0 0 12px;
}

.ai-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.ai-chip {
  border: 1px solid var(--ap-hairline-strong, rgba(0, 0, 0, 0.14));
  background: var(--ap-surface, #fff);
  color: var(--ap-text, #1d1d1f);
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.ai-chip:hover {
  border-color: var(--ap-blue, #0071e3);
  color: var(--ap-blue, #0071e3);
  background: var(--ap-blue-soft, rgba(0, 113, 227, 0.1));
}

.ai-intro-tip {
  margin: 14px 0 0;
  font-size: 11px;
  line-height: 1.6;
  color: var(--ap-text-quaternary, #a1a1a6);
}

.ai-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 100%;
}

.ai-row.user {
  align-items: flex-end;
}

.ai-bubble {
  max-width: 86%;
  padding: 9px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.65;
  word-break: break-word;
  white-space: pre-wrap;
}

.ai-row.user .ai-bubble {
  background: var(--ap-blue, #0071e3);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.ai-row.assistant .ai-bubble {
  background: var(--ap-surface, #fff);
  color: var(--ap-text, #1d1d1f);
  border: 1px solid var(--ap-hairline, rgba(0, 0, 0, 0.08));
  border-bottom-left-radius: 4px;
}

.ai-dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  height: 14px;
}

.ai-dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ap-text-quaternary, #a1a1a6);
  animation: ai-blink 1.1s infinite ease-in-out;
}

.ai-dots i:nth-child(2) { animation-delay: 0.16s; }
.ai-dots i:nth-child(3) { animation-delay: 0.32s; }

@keyframes ai-blink {
  0%, 80%, 100% { opacity: 0.25; transform: translateY(0); }
  40% { opacity: 1; transform: translateY(-2px); }
}

/* ---------- 工具调用卡片 ---------- */
.ai-toolcard {
  width: 100%;
  border: 1px solid var(--ap-hairline, rgba(0, 0, 0, 0.08));
  border-radius: 12px;
  background: var(--ap-surface, #fff);
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-tool-top {
  display: flex;
  align-items: baseline;
  gap: 8px;
  overflow: hidden;
}

.ai-tool-name {
  flex: none;
  font-size: 11px;
  font-weight: 600;
  color: var(--ap-blue, #0071e3);
  background: var(--ap-blue-soft, rgba(0, 113, 227, 0.1));
  padding: 2px 8px;
  border-radius: 6px;
}

.ai-tool-args {
  font-size: 11px;
  color: var(--ap-text-secondary, #6e6e73);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-link {
  align-self: flex-start;
  border: none;
  background: none;
  padding: 0;
  cursor: pointer;
  font-size: 11px;
  color: var(--ap-blue, #0071e3);
}

.ai-link:hover {
  text-decoration: underline;
}

.ai-json {
  margin: 0;
  max-height: 220px;
  overflow: auto;
  padding: 10px;
  border-radius: 8px;
  background: var(--ap-bg, #f5f5f7);
  border: 1px solid var(--ap-hairline, rgba(0, 0, 0, 0.08));
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  line-height: 1.55;
  color: var(--ap-text-secondary, #6e6e73);
  white-space: pre;
}

.ai-result {
  font-size: 12px;
  padding: 6px 10px;
  border-radius: 8px;
}

.ai-result.ok {
  color: #1a7f37;
  background: rgba(52, 199, 89, 0.12);
}

.ai-result.fail {
  color: #c9252d;
  background: rgba(255, 59, 48, 0.1);
}

/* ---------- 输入区 ---------- */
.ai-foot {
  flex: none;
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 14px;
  border-top: 1px solid var(--ap-hairline, rgba(0, 0, 0, 0.08));
  background: var(--ap-surface, #fff);
}

.ai-input {
  flex: 1;
  resize: none;
  border: 1px solid var(--ap-hairline-strong, rgba(0, 0, 0, 0.14));
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--ap-text, #1d1d1f);
  background: var(--ap-surface, #fff);
  outline: none;
  font-family: inherit;
  max-height: 120px;
}

.ai-input:focus {
  border-color: var(--ap-blue, #0071e3);
  box-shadow: 0 0 0 3px var(--ap-blue-soft, rgba(0, 113, 227, 0.1));
}

.ai-send {
  flex: none;
  height: 34px;
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  background: var(--ap-blue, #0071e3);
  transition: opacity 0.18s ease;
}

.ai-send:hover:not(:disabled) {
  background: var(--ap-blue-hover, #0077ed);
}

.ai-send:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ai-send.is-stop {
  background: var(--ap-text-secondary, #6e6e73);
}

/* ---------- 动画 ---------- */
.ai-slide-enter-active,
.ai-slide-leave-active {
  transition: opacity 0.22s ease, transform 0.22s var(--ap-ease, ease);
}

.ai-slide-enter-from,
.ai-slide-leave-to {
  opacity: 0;
  transform: translateX(18px);
}
</style>
