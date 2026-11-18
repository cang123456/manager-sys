package com.ruoyi.web.ai;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * AI 助手接口（LangChain4j 版）
 *
 * POST /ai/chat        → SSE：chunk(文字增量) / tool(工具调用) / done / error
 * POST /ai/tool-result → 前端执行完工具后把结果写回记忆
 * POST /ai/reset       → 清空指定会话的记忆
 *
 * 均需登录（沿用若依 JWT 鉴权）。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/ai")
public class AiChatController
{
    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService)
    {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter chat(@RequestBody AiChatRequest request)
    {
        // 必须在当前请求线程取 userId：SSE 推送跑在线程池里，那里没有 SecurityContext
        Long userId = SecurityUtils.getUserId();
        return aiChatService.stream(request.getSessionId(), request.getMessage(), userId);
    }

    @PostMapping("/tool-result")
    public AjaxResult toolResult(@RequestBody Map<String, Object> body)
    {
        // 同样必须在请求线程取 userId：记忆 key 是 (userId, sessionId)
        Long userId = SecurityUtils.getUserId();
        aiChatService.addToolResult(
                userId,
                str(body.get("sessionId")),
                str(body.get("toolCallId")),
                str(body.get("toolName")),
                str(body.get("result")));
        return AjaxResult.success();
    }

    @PostMapping("/reset")
    public AjaxResult reset(@RequestBody AiChatRequest request)
    {
        Long userId = SecurityUtils.getUserId();
        aiChatService.reset(userId, request.getSessionId());
        return AjaxResult.success();
    }

    @GetMapping("/status")
    public AjaxResult status()
    {
        return AjaxResult.success(aiChatService.storageInfo());
    }

    private static String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }
}
