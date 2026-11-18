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
