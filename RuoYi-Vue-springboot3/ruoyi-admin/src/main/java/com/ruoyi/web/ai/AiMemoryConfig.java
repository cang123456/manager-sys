package com.ruoyi.web.ai;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * 会话记忆存储的装配
 *
 * ai.assistant.memory-store 决定用哪个实现：
 *   redis （默认）→ RedisChatMemoryStore：跨重启保留、多实例共享
 *   memory        → AiMemoryStore：进程内 Map，重启即丢
 *
 * 换存储只改这一行配置，AiChatService、记忆窗口、工具协议、SSE 全都不用动 ——
 * 因为 LangChain4j 只要求实现 ChatMemoryStore 的三个方法。
 *
 * @author ruoyi
 */
@Configuration
public class AiMemoryConfig
{
    @Bean
    @ConditionalOnProperty(name = "ai.assistant.memory-store", havingValue = "memory")
    public ChatMemoryStore memoryChatMemoryStore()
    {
        return new AiMemoryStore();
    }

    @Bean
    @ConditionalOnProperty(name = "ai.assistant.memory-store", havingValue = "redis", matchIfMissing = true)
    public ChatMemoryStore redisChatMemoryStore(StringRedisTemplate redisTemplate, AiAssistantProperties properties)
    {
        return new RedisChatMemoryStore(redisTemplate, Duration.ofHours(properties.getMemoryTtlHours()));
    }
}
