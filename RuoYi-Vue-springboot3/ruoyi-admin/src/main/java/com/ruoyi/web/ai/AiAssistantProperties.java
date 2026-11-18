package com.ruoyi.web.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 助手配置
 *
 * 百炼（DashScope）OpenAI 兼容端点配置。
 * api-key 不写进 application.yml（该文件在版本库里），
 * 而是从 ai-local.yml 或环境变量 AI_ASSISTANT_API_KEY 读取。
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ai.assistant")
public class AiAssistantProperties
{
    /** OpenAI 兼容端点，例如 https://xxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1 */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 百炼 API Key */
    private String apiKey;

    /** 默认模型 */
    private String model = "qwen-plus";

    /** 单次请求超时（秒） */
    private int timeoutSeconds = 120;

    /** 每个会话保留的最近消息条数（LangChain4j 的滑窗记忆） */
    private int memoryMaxMessages = 12;

    /**
     * 会话记忆的存储方式：redis（默认）或 memory
     *
     * redis ：跨重启保留、多实例共享（生产用）
     * memory：进程内 Map，重启即丢（本地调试、没有 Redis 时用）
     */
    private String memoryStore = "redis";

    /** 会话记忆在 Redis 里的存活时间（小时），默认 72 小时 */
    private int memoryTtlHours = 72;

    public int getMemoryMaxMessages()
    {
        return memoryMaxMessages;
    }

    public void setMemoryMaxMessages(int memoryMaxMessages)
    {
        this.memoryMaxMessages = memoryMaxMessages;
    }

    public String getMemoryStore()
    {
        return memoryStore;
    }

    public void setMemoryStore(String memoryStore)
    {
        this.memoryStore = memoryStore;
    }

    public int getMemoryTtlHours()
    {
        return memoryTtlHours;
    }

    public void setMemoryTtlHours(int memoryTtlHours)
    {
        this.memoryTtlHours = memoryTtlHours;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public int getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }
}
