import router from '@/router'
import usePermissionStore from '@/store/modules/permission'

/**
 * 拼出绝对路径：父 '/system' + 子 'dept' => '/system/dept'
 * 若依的菜单树里，子路由 path 都是相对的，必须逐层累加，
 * 否则模型拿到的是 'dept' 这种值，router.push 会按当前页面做相对解析而跑偏。
 */
function joinPath(parent, path) {
  if (!path) {
    return parent || '/'
  }
  if (path.startsWith('/')) {
    return path
  }
  return (parent === '/' ? '' : parent || '') + '/' + path
}

/** 从若依菜单树累加出扁平清单（绝对路径 + 菜单名） */
function walkMenus(routes, base, out, seen) {
  for (const route of routes || []) {
    if (!route || route.hidden === true) {
      continue
    }
    const full = joinPath(base, route.path)
    const meta = route.meta || {}
    const title = meta.title
    if (title && !String(full).includes(':') && !seen.has(full)) {
      seen.add(full)
      out.push({ path: full, title: String(title) })
    }
    if (route.children && route.children.length) {
      walkMenus(route.children, full, out, seen)
    }
  }
}

/**
 * 当前账号真实可访问的菜单列表
 *
 * 模型不可能天生知道本系统的路由约定（实测 gui-plus 会编出 "userManage" 这种假路由），
 * 所以必须把真实清单交给它，而不是让它猜。
 */
export function getRouteCatalog() {
  const out = []
  const seen = new Set()
  try {
    const store = usePermissionStore()
    walkMenus(store.sidebarRouters, '', out, seen)
  } catch (e) {
    // store 不可用时退回到 router 注册表
  }
  if (!out.length) {
    for (const route of router.getRoutes()) {
      const meta = route.meta || {}
      const path = route.path || ''
      if (!path || path.includes(':') || !meta.title || seen.has(path)) {
        continue
      }
      seen.add(path)
      out.push({ path, title: String(meta.title) })
    }
  }
  return out.sort((a, b) => a.path.localeCompare(b.path))
}

/** 拼成给模型的清单文本 */
export function buildRouteCatalogText() {
  const list = getRouteCatalog()
  if (!list.length) {
    return ''
  }
  return '当前账号可访问的菜单如下（route 只能取这里的值，禁止自行编造、禁止写英文变量名）：\n'
    + list.map(item => `- ${item.path}  ${item.title}`).join('\n')
}

/** 按标题反查路由，用于模型给了假路由时的兜底 */
function findByTitle(title) {
  const key = String(title || '').trim()
  if (!key) {
    return null
  }
  const list = getRouteCatalog()
  return list.find(item => item.title === key)
    || list.find(item => item.title.includes(key) || key.includes(item.title))
    || null
}

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
        description: '跳转到系统内的某个菜单页面。route 必须严格取自系统提供的菜单清单，不能自己编造。',
        parameters: {
          type: 'object',
          properties: {
            route: {
              type: 'string',
              description: '路由路径，必须来自菜单清单，例如 /system/user（用户管理）、/index（首页）'
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
      const matched = resolved && resolved.matched && resolved.matched.length > 0

      if (matched) {
        await router.push(target)
        return { ok: true, route: target }
      }

      // 兜底：模型给了假路由（如 userManage），用中文名反查真实路径
      const fallback = findByTitle(args.title)
      if (fallback) {
        await router.push(fallback.path)
        return { ok: true, route: fallback.path, corrected: true }
      }

      throw new Error(`路由 ${target} 不存在，且按名称「${args.title || '空'}」也没找到对应菜单`)
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
