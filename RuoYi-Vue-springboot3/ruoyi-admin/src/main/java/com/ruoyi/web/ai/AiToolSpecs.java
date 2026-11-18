package com.ruoyi.web.ai;

import java.util.List;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

/**
 * 工具定义（LangChain4j 的 ToolSpecification）
 *
 * 注意：这里只描述"能做什么"，真正执行在浏览器里。
 * 因为工具动作本身就是前端行为（router.push 跳路由），
 * 后端拿到模型返回的 ToolExecutionRequest 后原样下发给前端执行。
 *
 * @author ruoyi
 */
public final class AiToolSpecs
{
    public static final String NAVIGATE = "navigate";

    private AiToolSpecs()
    {
    }

    public static List<ToolSpecification> all()
    {
        return List.of(navigate());
    }

    public static ToolSpecification navigate()
    {
        JsonObjectSchema parameters = JsonObjectSchema.builder()
                .addStringProperty("route", "路由路径，必须严格取自系统提供的菜单清单，例如 /system/user")
                .addStringProperty("title", "菜单的中文名称，例如 用户管理")
                .required("route")
                .additionalProperties(false)
                .build();

        return ToolSpecification.builder()
                .name(NAVIGATE)
                .description("跳转到系统内的某个菜单页面。route 只能取自菜单清单，禁止自行编造。"
                        + "当用户只是提问、闲聊、或询问某个功能怎么用时，不要调用本工具，直接用文字回答。")
                .parameters(parameters)
                .build();
    }
}
