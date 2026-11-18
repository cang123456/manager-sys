package com.ruoyi.web.ai;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 助手接口
 *
 * POST /ai/chat  → text/event-stream
 * 需要登录（走若依现有 JWT 鉴权），前端带 Authorization 头调用。
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

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AiChatRequest request)
    {
        return aiChatService.stream(request);
    }
}
