package com.ruoyi.web.ai;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 会话记忆存 Redis
 *
 * 解决 AiMemoryStore 的两个硬伤：
 * 1. 进程重启后全部丢失，用户会说"它怎么突然不记得了"
 * 2. 多实例部署时请求落到另一台机器，记忆不共享
 *
 * key 直接复用 MemoryKey 的 toString()，形如：
 *   ai:memory:1:s-m3k2abcd-9x1a2b
 * 前面带 userId，所以记忆隔离在存储层天然继承。
 *
 * 序列化必须用框架提供的 ChatMessageSerializer / ChatMessageDeserializer：
 * ChatMessage 是多态类型（UserMessage / AiMessage / ToolExecutionResultMessage），
 * 自己 toString 会丢掉 AiMessage 里的 toolExecutionRequests，反序列化后
 * 下一轮请求就会因为"有 tool_calls 却没有 tool 结果"被协议拒绝（400）。
 *
 * 用 StringRedisTemplate 而不是项目里的 RedisTemplate<Object,Object>：
 * 后者值是 FastJson2 序列化的，存一段 JSON 字符串会被再包一层引号并转义，
 * 白白膨胀十几 %，排查时用 redis-cli 看到的也不是可读内容。
 *
 * @author ruoyi
 */
public class RedisChatMemoryStore implements ChatMemoryStore
{
    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);

    private static final String KEY_PREFIX = "ai:memory:";

    private final StringRedisTemplate redis;

    private final Duration ttl;

    public RedisChatMemoryStore(StringRedisTemplate redis, Duration ttl)
    {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId)
    {
        String key = key(memoryId);
        String json = redis.opsForValue().get(key);
        if (json == null || json.isEmpty())
        {
            return Collections.emptyList();
        }
        try
        {
            return ChatMessageDeserializer.messagesFromJson(json);
        }
        catch (Exception e)
        {
            // 历史数据格式不兼容时，不能让整轮对话 500：清掉它，当作新会话
            log.warn("[AI] 会话记忆反序列化失败，按空会话处理：key={}，原因={}", key, e.getMessage());
            redis.delete(key);
            return Collections.emptyList();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages)
    {
        String key = key(memoryId);
        try
        {
            String json = ChatMessageSerializer.messagesToJson(messages);
            redis.opsForValue().set(key, json, ttl);
        }
        catch (Exception e)
        {
            // 写失败不该打断对话：本次仍能正常回答，只是下一轮记不住
            log.error("[AI] 会话记忆写入 Redis 失败：key={}", key, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId)
    {
        redis.delete(key(memoryId));
    }

    private String key(Object memoryId)
    {
        return KEY_PREFIX + memoryId;
    }
}
