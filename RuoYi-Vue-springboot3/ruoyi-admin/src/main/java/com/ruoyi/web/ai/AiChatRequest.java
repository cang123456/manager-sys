package com.ruoyi.web.ai;

/**
 * AI 对话请求
 *
 * 相比手写版本，前端不再传 messages / tools：
 * 记忆由后端按 sessionId 维护，工具定义由后端 ToolSpecification 提供，
 * 前端只说"我是哪个会话、我这句话是什么"。
 *
 * @author ruoyi
 */
public class AiChatRequest
{
    /** 会话标识，前端生成并持久化（localStorage），用于隔离不同用户的记忆 */
    private String sessionId;

    /** 用户这句话 */
    private String message;

    public String getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(String sessionId)
    {
        this.sessionId = sessionId;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }
}
