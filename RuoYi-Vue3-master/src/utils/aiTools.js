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

/** 跳转失败时把诊断信息打到控制台，方便 F12 一眼定位 */
function fail(message, debug) {
  if (typeof console !== 'undefined') {
    console.warn('[AI] navigate 失败：' + message, debug)
  }
  throw new Error(message)
}

/** 去掉 query 与结尾斜杠，用于比较"是不是同一个页面" */
function normalizePath(path) {
  const p = String(path || '').split('?')[0].replace(/\/+$/, '')
  return p || '/'
}

function isSamePage(a, b) {
  return normalizePath(a) === normalizePath(b)
}

/**
 * 这个路径能不能解析出真实页面
 *
 * 不能用 getRoutes().some(r => r.path === path) 判断：
 * 动态注册的路由在某些时序下（比如开发态热更新重建 router 实例）
 * 会从注册表里消失，但地址栏和页面还在，于是误报"路由未注册"。
 * 用 resolve 的 matched 判断更贴近"打开它会发生什么"。
 */
function isRealRoute(path) {
  const matched = (router.resolve(path).matched || [])
  return matched.length > 0 && !matched.some(m => String(m.path).includes('pathMatch'))
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
        fail('模型没有给出路由', { 收到参数: args })
      }
      const target = raw.startsWith('/') ? raw : '/' + raw
      const before = router.currentRoute.value.fullPath
      const registered = router.getRoutes()

      const debug = {
        目标路由: target,
        跳转前: before,
        已注册路由数: registered.length,
        解析匹配数: (router.resolve(target).matched || []).length
      }

      // 已经在目标页面就直接算成功。
      // 之前出现过"地址栏就是 /system/user、页面也正常，却提示打不开"的情况：
      // 当时是注册表里临时缺了这一条，但我们其实根本不需要跳转。
      if (isSamePage(before, target)) {
        debug.说明 = '当前已在目标页面，无需跳转'
        return { ok: true, route: before, debug }
      }

      // 先确认这个路径真能解析出页面，否则 push 只会改地址栏不渲染内容
      let finalTarget = target
      if (!isRealRoute(target)) {
        const fallback = findByTitle(args.title)
        // 失败时把注册表样本一起带出去，一次就能看出路径对不对
        debug.注册表样本 = registered
          .map(r => r.path)
          .filter(p => p && p !== '/' && !p.includes(':'))
          .slice(0, 20)
        if (!fallback) {
          // 注册表条数异常少，基本可以断定动态菜单路由没加载进来
          const hint = registered.length < 20
            ? `（前端路由表只有 ${registered.length} 条，动态菜单路由可能没注册，按 F5 刷新后重试）`
            : ''
          fail(`路由 ${target} 解析不到页面${hint}，按名称「${args.title || '空'}」也没找到菜单`, debug)
        }
        finalTarget = fallback.path
        debug.兜底命中 = fallback.path
      }

      try {
        await router.push(finalTarget)
      } catch (e) {
        fail(`router.push 失败：${e && e.message}`, debug)
      }

      const after = router.currentRoute.value
      const matchedList = after.matched || []
      const hitCatchAll = matchedList.some(m => String(m.path).includes('pathMatch'))
      debug.跳转后 = after.fullPath
      debug.跳转后匹配数 = matchedList.length
      if (hitCatchAll) {
        debug.命中兜底404 = true
      }

      // 命中 404 兜底路由 = 路径是野的（地址栏会变，但页面渲染成 404/空白）
      if (!matchedList.length || hitCatchAll) {
        try {
          await router.replace(before || '/index')
        } catch (ignore) {
          // 回滚失败也不掩盖原始错误
        }
        fail(`路径 ${finalTarget} 没有对应页面（命中了 404 兜底），已回滚到 ${before}`, debug)
      }

      return { ok: true, route: after.fullPath, debug }
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
