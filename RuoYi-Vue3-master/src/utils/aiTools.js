import router from '@/router'

/**
 * AI 助手的工具注册表
 *
 * 这里是唯一的扩展点：以后要加"打开弹窗""切主题""查数据"，
 * 只在这个对象里加一项 { schema, handler } 即可，
 * 后端、组件、请求流程都不用改。
 *
 * schema  : 发给大模型的 OpenAI function 定义
 * describe: 生成给用户看的一句话（流式工具调用时模型不说话，由这里补文案）
 * handler : 浏览器里真正执行的动作，返回 { ok, ... } 或抛错
 */
const toolRegistry = {
  navigate: {
    schema: {
      type: 'function',
      function: {
        name: 'navigate',
        description: '跳转到系统内的某个菜单页面。当用户说「去/打开/看看 某某 页面」「回到首页」时调用。只能使用系统里真实存在的路由，不要自己编造。',
        parameters: {
          type: 'object',
          properties: {
            route: {
              type: 'string',
              description: '路由路径，必须以 / 开头，例如 /system/user（用户管理）、/system/role（角色管理）、/system/menu（菜单管理）、/index（首页）'
            },
            title: {
              type: 'string',
              description: '菜单的中文名称，例如 用户管理'
            }
          },
          required: ['route']
        }
      }
    },
    describe: (args) => `好的，这就为你打开「${args.title || args.route}」。`,
    handler: async (args) => {
      const raw = String(args.route || '').trim()
      if (!raw) {
        throw new Error('模型没有给出路由')
      }
      const target = raw.startsWith('/') ? raw : '/' + raw
      const resolved = router.resolve(target)
      if (!resolved || !resolved.matched || resolved.matched.length === 0) {
        throw new Error(`路由 ${target} 不存在，或当前账号没有该菜单权限`)
      }
      await router.push(target)
      return { ok: true, route: target }
    }
  }
}

/** 发给后端的工具定义数组 */
export function getToolSchemas() {
  return Object.values(toolRegistry).map(item => item.schema)
}

/** 工具是否存在 */
export function hasTool(name) {
  return Object.prototype.hasOwnProperty.call(toolRegistry, name)
}

/** 根据工具调用生成一句给用户看的文案 */
export function describeToolCall(name, args) {
  const tool = toolRegistry[name]
  if (!tool) {
    return `模型调用了未知工具「${name}」。`
  }
  try {
    return tool.describe ? tool.describe(args || {}) : `正在执行「${name}」。`
  } catch (e) {
    return `正在执行「${name}」。`
  }
}

/** 执行工具，失败时返回 { ok:false, error } */
export async function executeTool(name, args) {
  const tool = toolRegistry[name]
  if (!tool) {
    return { ok: false, error: `未注册的工具：${name}` }
  }
  try {
    const result = await tool.handler(args || {})
    return { ok: true, ...(result || {}) }
  } catch (e) {
    return { ok: false, error: e && e.message ? e.message : String(e) }
  }
}
