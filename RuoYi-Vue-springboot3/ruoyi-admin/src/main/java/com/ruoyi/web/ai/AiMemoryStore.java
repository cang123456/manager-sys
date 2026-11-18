package com.ruoyi.web.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 会话记忆存放在进程内（ConcurrentHashMap）
 *
 * key 是 MemoryKey(userId, sessionId)，值是该会话最近若干条消息。
 * 空闲超过 2 小时由 purgeExpired() 回收。
 *
 * 这是 ai.assistant.memory-store=memory 时使用的实现，适合本地调试，
 * 或缺 Redis 的临时环境。生产默认走 RedisChatMemoryStore：
 * 进程重启不丢、多实例共享。两者都只实现 ChatMemoryStore 的三个方法，
 * 换实现不影响 AiChatService 的任何逻辑。
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
