package com.ruoyi.web.ai;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.utils.SecurityUtils;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/**
 * AI 助手服务（基于 LangChain4j）
 *
 * 交给框架的三件事：
 * 1. 对话记忆：MessageWindowChatMemory + 自实现 AiMemoryStore，按 sessionId 隔离
 * 2. 工具协议：ToolSpecification 声明能力；模型返回 ToolExecutionRequest 时参数已被框架拼好，
 *    前端不再需要累加 arguments 分片（那是之前最容易出错的地方）
 * 3. 流式解析：StreamingChatResponseHandler 只暴露 onPartialResponse / onCompleteResponse，
 *    SSE 分帧、[DONE]、错误包装都在框架内部
 *
 * 没交出去的是"工具执行"：跳路由是浏览器行为，后端只把 ToolExecutionRequest 下发，
 * 前端执行完把结果回报进来写进记忆。
 *
 * @author ruoyi
 */
@Service
public class AiChatService
{
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final AiAssistantProperties properties;

    private final AiMenuCatalog menuCatalog;

    private final AiMemoryStore memoryStore = new AiMemoryStore();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final StreamingChatModel model;

    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-chat");
        t.setDaemon(true);
        return t;
    });

    /** 模型构建失败（如未配置 key）时的原因，请求时直接回给前端 */
    private final String initError;

    public AiChatService(AiAssistantProperties properties, AiMenuCatalog menuCatalog)
    {
        this.properties = properties;
        this.menuCatalog = menuCatalog;

        StreamingChatModel built = null;
        String error = null;
        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
        {
            error = "未配置百炼 API Key：请在 ai-local.yml 或环境变量 AI_ASSISTANT_API_KEY 中配置";
        }
        else
        {
            try
            {
                built = OpenAiStreamingChatModel.builder()
                        .baseUrl(properties.getBaseUrl())
                        .apiKey(properties.getApiKey())
                        .modelName(properties.getModel())
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .logRequests(false)
                        .logResponses(false)
                        .build();
            }
            catch (Exception e)
            {
                error = "初始化模型失败：" + e.getMessage();
                log.error("[AI] 初始化 StreamingChatModel 失败", e);
            }
        }
        this.model = built;
        this.initError = error;
    }

    /** 一轮对话，返回 SSE 流 */
    public SseEmitter stream(String sessionId, String message, Long userId)
    {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (model == null)
        {
            pool.submit(() -> {
                sendError(emitter, initError);
                completeQuietly(emitter);
            });
            return emitter;
        }
        if (sessionId == null || sessionId.isBlank() || message == null || message.isBlank())
        {
            pool.submit(() -> {
                sendError(emitter, "sessionId 或 message 为空");
                completeQuietly(emitter);
            });
            return emitter;
        }

        pool.submit(() -> run(sessionId, message.trim(), userId, emitter));
        return emitter;
    }

    private void run(String sessionId, String message, Long userId, SseEmitter emitter)
    {
        ChatMemory memory = memoryOf(sessionId);
        try
        {
            memory.add(UserMessage.from(message));

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(buildSystemPrompt(userId)));
            messages.addAll(fillDanglingToolResults(memory));

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .toolSpecifications(AiToolSpecs.all())
                    .build();

            model.chat(request, new StreamingChatResponseHandler()
            {
                @Override
                public void onPartialResponse(String partial)
                {
                    if (partial == null || partial.isEmpty())
                    {
                        return;
                    }
                    try
                    {
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("text", partial);
                        emitter.send(SseEmitter.event().name("chunk").data(objectMapper.writeValueAsString(payload)));
                    }
                    catch (Exception e)
                    {
                        log.debug("[AI] 写回前端失败：{}", e.getMessage());
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse response)
                {
                    try
                    {
                        AiMessage ai = response.aiMessage();
                        memory.add(ai);

                        if (ai.hasToolExecutionRequests())
                        {
                            emitter.send(SseEmitter.event().name("tool").data(objectMapper.writeValueAsString(toolPayload(response))));
                        }
                        emitter.send(SseEmitter.event().name("done").data("{}"));
                        completeQuietly(emitter);
                    }
                    catch (Exception e)
                    {
                        log.warn("[AI] 收尾失败：{}", e.getMessage());
                        completeQuietly(emitter);
                    }
                }

                @Override
                public void onError(Throwable error)
                {
                    log.error("[AI] 模型调用失败", error);
                    sendError(emitter, describeFailure(error));
                    completeQuietly(emitter);
                }
            });
        }
        catch (Exception e)
        {
            log.error("[AI] 对话处理失败", e);
            sendError(emitter, describeFailure(e));
            completeQuietly(emitter);
        }
    }

    /** 前端执行完工具后回报结果，写进记忆，下一轮模型就知道已经跳过了 */
    public void addToolResult(String sessionId, String toolCallId, String toolName, String resultText)
    {
        if (sessionId == null || sessionId.isBlank())
        {
            return;
        }
        memoryOf(sessionId).add(new ToolExecutionResultMessage(toolCallId, toolName,
                resultText == null || resultText.isBlank() ? "ok" : resultText));
    }

    /** 清空某个会话的记忆 */
    public void reset(String sessionId)
    {
        memoryStore.deleteMessages(sessionId);
    }

    public int activeSessions()
    {
        return memoryStore.size();
    }

    private ChatMemory memoryOf(String sessionId)
    {
        return MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(properties.getMemoryMaxMessages())
                .chatMemoryStore(memoryStore)
                .build();
    }

    /**
     * 补齐缺失的工具结果
     *
     * OpenAI 协议要求：带 tool_calls 的 assistant 消息后面必须跟 tool 消息，
     * 否则下一次请求直接 400。前端没回报时（关页面、点停止）这里补一条占位。
     */
    private List<ChatMessage> fillDanglingToolResults(ChatMemory memory)
    {
        List<ChatMessage> list = new ArrayList<>(memory.messages());
        if (!list.isEmpty() && list.get(list.size() - 1) instanceof AiMessage ai && ai.hasToolExecutionRequests())
        {
            for (ToolExecutionRequest request : ai.toolExecutionRequests())
            {
                ToolExecutionResultMessage placeholder = new ToolExecutionResultMessage(
                        request.id(), request.name(), "（前端未回报执行结果）");
                memory.add(placeholder);
                list.add(placeholder);
            }
        }
        return list;
    }

    private String buildSystemPrompt(Long userId)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("你是若依后台的智能助手，帮用户在系统内跳转页面。\n");
        sb.append("规则：\n");
        sb.append("1. 用户明确要求打开某个页面时，调用 navigate 工具。\n");
        sb.append("2. 用户只是在提问、闲聊、或问某个功能怎么用时，直接用文字回答，不要调用工具。\n");
        sb.append("3. route 必须严格取自下面清单，禁止编造英文变量名。\n");
        sb.append("4. 始终以用户最新一条消息为准，不要沿用上一轮打开过的菜单。\n");
        try
        {
            String catalog = menuCatalog.textForUser(userId);
            if (!catalog.isEmpty())
            {
                sb.append('\n').append(catalog);
            }
            else
            {
                log.warn("[AI] 菜单清单为空，模型可能编造路由（userId={}）", userId);
            }
        }
        catch (Exception e)
        {
            log.warn("[AI] 读取菜单清单失败：{}", e.getMessage());
        }
        return sb.toString();
    }

    private Map<String, Object> toolPayload(ChatResponse response)
    {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (ToolExecutionRequest request : response.aiMessage().toolExecutionRequests())
        {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", request.id());
            one.put("name", request.name());
            one.put("arguments", request.arguments());
            calls.add(one);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", response.modelName());
        payload.put("requestId", response.id());
        payload.put("toolCalls", calls);
        return payload;
    }

    private String describeFailure(Throwable error)
    {
        String msg = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (msg.contains("BudgetLimitExceeded") || msg.contains("budget"))
        {
            return "百炼预算已用尽：请到控制台『预算管理』提高或重置预算后再试";
        }
        if (msg.contains("AccessDenied"))
        {
            return "当前账号未开通该模型";
        }
        if (msg.contains("connect timed out") || msg.contains("Connection refused"))
        {
            return "连不上百炼（网络连接超时），请稍后再试";
        }
        return "调用失败：" + msg;
    }

    private void sendError(SseEmitter emitter, String message)
    {
        try
        {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(payload)));
        }
        catch (Exception ignored)
        {
            // 连接已断
        }
    }

    private void completeQuietly(SseEmitter emitter)
    {
        try
        {
            emitter.complete();
        }
        catch (Exception ignored)
        {
            // 已经完成
        }
    }
}
