package com.ruoyi.web.ai;

import java.util.List;
import java.util.Map;

/**
 * AI 对话请求体
 *
 * 后端只做一个透明代理：不认识任何具体工具，
 * 前端传来什么 messages / tools，就原样转给百炼。
 * 这样以后新增工具只需要改前端的工具注册表，后端不用动。
 *
 * @author ruoyi
 */
public class AiChatRequest
{
    /** 对话历史，格式同 OpenAI：{role, content} */
    private List<Map<String, Object>> messages;

    /** 工具定义（OpenAI function 格式），可为空 */
    private List<Map<String, Object>> tools;

    /** 覆盖默认模型，可为空 */
    private String model;

    public List<Map<String, Object>> getMessages()
    {
        return messages;
    }

    public void setMessages(List<Map<String, Object>> messages)
    {
        this.messages = messages;
    }

    public List<Map<String, Object>> getTools()
    {
        return tools;
    }

    public void setTools(List<Map<String, Object>> tools)
    {
        this.tools = tools;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }
}
