package com.ruoyi.web.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.system.domain.vo.RouterVo;
import com.ruoyi.system.service.ISysMenuService;

/**
 * 菜单清单（给模型看的"合法取值表"）
 *
 * 直接复用若依自己的菜单服务：selectMenuTreeByUserId + buildMenus，
 * 和前端 getRouters 走同一套逻辑，所以路径与权限天然一致。
 *
 * 之所以必须给模型这份清单：模型不知道本系统的路由约定，
 * 实测 gui-plus 会编出 userManage 这种英文变量名，跳转必然失败。
 *
 * @author ruoyi
 */
@Service
public class AiMenuCatalog
{
    /** 清单缓存 60 秒，避免每轮对话都查库 */
    private static final long CACHE_TTL_MS = 60_000L;

    private final ISysMenuService menuService;

    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public AiMenuCatalog(ISysMenuService menuService)
    {
        this.menuService = menuService;
    }

    /** 生成给模型看的清单文本 */
    public String textForUser(Long userId)
    {
        List<Item> items = itemsForUser(userId);
        if (items.isEmpty())
        {
            return "";
        }
        StringBuilder sb = new StringBuilder("当前账号可访问的菜单如下（route 只能取这里的值，禁止自行编造）：\n");
        for (Item item : items)
        {
            sb.append("- ").append(item.path).append("  ").append(item.title).append('\n');
        }
        return sb.toString();
    }

    /** 当前用户可见菜单（绝对路径 + 标题） */
    public List<Item> itemsForUser(Long userId)
    {
        long now = System.currentTimeMillis();
        CacheEntry entry = cache.get(userId);
        if (entry != null && now - entry.time < CACHE_TTL_MS)
        {
            return entry.items;
        }
        List<Item> items = load(userId);
        cache.put(userId, new CacheEntry(items, now));
        return items;
    }

    private List<Item> load(Long userId)
    {
        List<Item> out = new ArrayList<>();
        try
        {
            List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
            List<RouterVo> routers = menuService.buildMenus(menus);
            walk(routers, "", out, new HashSet<>());
        }
        catch (Exception e)
        {
            // 拿不到菜单不该让整次对话失败，退化成没有清单
        }
        return out;
    }

    private void walk(List<RouterVo> routers, String base, List<Item> out, Set<String> seen)
    {
        if (routers == null)
        {
            return;
        }
        for (RouterVo router : routers)
        {
            String path = router.getPath();
            if (path == null || path.isEmpty())
            {
                continue;
            }
            String full;
            if (path.startsWith("/"))
            {
                full = path;
            }
            else if (base.isEmpty())
            {
                full = "/" + path;
            }
            else
            {
                full = base + "/" + path;
            }

            boolean hidden = Boolean.TRUE.equals(router.getHidden());
            String title = router.getMeta() == null ? null : router.getMeta().getTitle();
            if (!hidden && title != null && !full.contains(":") && seen.add(full))
            {
                out.add(new Item(full, title));
            }
            walk(router.getChildren(), full, out, seen);
        }
    }

    /** 一条菜单 */
    public static class Item
    {
        public final String path;

        public final String title;

        public Item(String path, String title)
        {
            this.path = path;
            this.title = title;
        }
    }

    private static class CacheEntry
    {
        final List<Item> items;

        final long time;

        CacheEntry(List<Item> items, long time)
        {
            this.items = items;
            this.time = time;
        }
    }
}
