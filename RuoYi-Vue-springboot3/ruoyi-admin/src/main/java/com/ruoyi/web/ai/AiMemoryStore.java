package com.ruoyi.web.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 会话记忆存储
 *
 * LangChain4j 只带了一个单槽位的 SingleSlotChatMemoryStore，多用户多会话不够用，
 * 这里用 ConcurrentHashMap 按 sessionId 存，并做 2 小时的空闲回收。
 *
 * 之所以放在内存里：菜单跳转这种助手不需要跨重启保留历史；
 * 以后要持久化，把这里换成 Redis（若依现成有）即可，接口不用改。
 *
 * @author ruoyi
 */
public class AiMemoryStore implements ChatMemoryStore
{
    /** 空闲超过 2 小时回收 */
    private static final long IDLE_TTL_MS = 2 * 60 * 60 * 1000L;

    private final Map<Object, List<ChatMessage>> messages = new ConcurrentHashMap<>();

    private final Map<Object, Long> lastAccess = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> getMessages(Object memoryId)
    {
        purgeExpired();
        lastAccess.put(memoryId, System.currentTimeMillis());
        List<ChatMessage> list = messages.get(memoryId);
        return list == null ? java.util.Collections.emptyList() : list;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list)
    {
        messages.put(memoryId, list);
        lastAccess.put(memoryId, System.currentTimeMillis());
    }

    @Override
    public void deleteMessages(Object memoryId)
    {
        messages.remove(memoryId);
        lastAccess.remove(memoryId);
    }

    /** 当前活跃会话数，便于排查 */
    public int size()
    {
        return messages.size();
    }

    private void purgeExpired()
    {
        long now = System.currentTimeMillis();
        lastAccess.forEach((id, time) -> {
            if (now - time > IDLE_TTL_MS)
            {
                messages.remove(id);
                lastAccess.remove(id);
            }
        });
    }
}
