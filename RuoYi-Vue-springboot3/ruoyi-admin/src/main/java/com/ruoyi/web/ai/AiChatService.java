package com.ruoyi.web.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * AI 对话服务（百炼透明代理）
 *
 * 职责只有一件事：把前端的请求转发给百炼，把百炼返回的 SSE 分片原样转回前端。
 * 不解析工具调用、不拼参数、不执行业务动作 —— 这些都在前端做。
 *
 * 前端点「停止」或关闭页面时，会同步取消发往百炼的请求，
 * 避免上游连接悬着不释放（并发打满后会表现为下一次请求 Connection reset）。
 *
 * @author ruoyi
 */
@Service
public class AiChatService
{
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    /** SSE 连接超时：5 分钟 */
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final AiAssistantProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            // 强制 HTTP/1.1：SSE 走 HTTP/2 时，部分网关会在流中途重置连接
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    /** 转发线程池，避免阻塞 Tomcat 工作线程 */
    private final ExecutorService forwardPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ai-chat-forward");
        t.setDaemon(true);
        return t;
    });

    public AiChatService(AiAssistantProperties properties)
    {
        this.properties = properties;
    }

    /**
     * 发起一次流式对话，返回给控制器的 SseEmitter
     */
    public SseEmitter stream(AiChatRequest request)
    {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if (properties.getApiKey() == null || properties.getApiKey().isBlank())
        {
            forwardPool.submit(() -> {
                sendError(emitter, "未配置百炼 API Key：请在 ruoyi-admin/src/main/resources/ai-local.yml 中填写，或设置环境变量 AI_ASSISTANT_API_KEY");
                emitter.complete();
            });
            return emitter;
        }

        forwardPool.submit(() -> forward(request, emitter));
        return emitter;
    }

    private void forward(AiChatRequest request, SseEmitter emitter)
    {
        String requestBody;
        try
        {
            requestBody = buildBody(request);
        }
        catch (Exception e)
        {
            sendError(emitter, "构造请求失败：" + e.getMessage());
            emitter.complete();
            return;
        }

        String url = trimTrailingSlash(properties.getBaseUrl()) + "/chat/completions";
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        // 前端断开时用来取消上游请求
        final AtomicReference<CompletableFuture<HttpResponse<InputStream>>> futureRef = new AtomicReference<>();
        final AtomicReference<InputStream> upstreamRef = new AtomicReference<>();
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        /** 标记「是本次代码主动结束的」，用来区分正常收尾与前端中途断开 */
        final AtomicBoolean normalFinish = new AtomicBoolean(false);

        /** 主动结束：先置标记，再 complete()，保证 onCompletion 里读到的是 true */
        Runnable finish = () -> {
            normalFinish.set(true);
            try
            {
                emitter.complete();
            }
            catch (Exception ignored)
            {
                // 连接可能已经断了，complete() 抛异常不影响后续
            }
        };

        // 正常收尾：只释放资源，不打「前端断开」的日志
        Runnable releaseUpstream = () -> {
            CompletableFuture<HttpResponse<InputStream>> f = futureRef.get();
            if (f != null && !f.isDone())
            {
                f.cancel(true);
            }
            closeQuietly(upstreamRef.get());
        };

        // 异常收尾（前端点停止 / 关页面 / 超时）：取消上游并记一笔
        Runnable cancelUpstream = () -> {
            if (cancelled.compareAndSet(false, true))
            {
                log.debug("[AI] 前端已断开，已取消上游请求");
            }
            releaseUpstream.run();
        };

        emitter.onCompletion(() -> {
            if (normalFinish.get())
            {
                releaseUpstream.run();
            }
            else
            {
                // 走到这里说明不是我们自己结束的 —— 前端点了停止或关了页面
                cancelUpstream.run();
            }
        });
        emitter.onTimeout(cancelUpstream);
        emitter.onError(e -> cancelUpstream.run());

        try
        {
            HttpResponse<InputStream> response = null;
            Exception lastFailure = null;

            // 连接池里可能躺着一条「服务端已经关掉、客户端还以为能用」的空闲连接，
            // 复用它会得到 "HTTP/1.1 header parser received no bytes"。
            // 这种情况一个字节都还没写回浏览器，重试一次即可，不会重复计费也不会重复输出。
            for (int attempt = 1; attempt <= 2 && response == null; attempt++)
            {
                try
                {
                    CompletableFuture<HttpResponse<InputStream>> future = httpClient.sendAsync(
                            httpRequest, HttpResponse.BodyHandlers.ofInputStream());
                    futureRef.set(future);
                    response = future.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
                }
                catch (ExecutionException e)
                {
                    lastFailure = e.getCause() instanceof Exception ? (Exception) e.getCause() : e;
                    if (attempt == 1 && isRetryable(lastFailure))
                    {
                        log.warn("[AI] 与百炼的连接不可用，重试一次：{}", lastFailure.getMessage());
                        continue;
                    }
                    throw lastFailure;
                }
            }

            if (response == null)
            {
                throw lastFailure == null ? new IOException("未知原因未能建立连接") : lastFailure;
            }

            upstreamRef.set(response.body());

            if (cancelled.get())
            {
                closeQuietly(response.body());
                return;
            }

            if (response.statusCode() != 200)
            {
                String detail = readSafely(response.body());
                log.warn("[AI] 百炼返回 HTTP {}: {}", response.statusCode(), detail);
                sendError(emitter, describeHttpError(response.statusCode(), detail));
                finish.run();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (cancelled.get())
                    {
                        log.debug("[AI] 已取消，停止读取上游");
                        return;
                    }
                    if (line.isEmpty() || !line.startsWith("data:"))
                    {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data))
                    {
                        break;
                    }
                    sendEvent(emitter, "chunk", data);
                }
            }

            sendEvent(emitter, "done", "{}");
            finish.run();
        }
        catch (CancellationException e)
        {
            log.debug("[AI] 上游请求已被取消");
            finish.run();
        }
        catch (TimeoutException e)
        {
            log.warn("[AI] 等待百炼响应超时（{}s）", properties.getTimeoutSeconds());
            sendError(emitter, "调用超时（" + properties.getTimeoutSeconds() + "s），模型可能正在排队，稍后重试");
            finish.run();
        }
        catch (IllegalStateException e)
        {
            // 前端主动断开（点了停止 / 关掉页面），属正常情况
            log.debug("[AI] 客户端已断开: {}", e.getMessage());
            cancelUpstream.run();
            finish.run();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            cancelUpstream.run();
            finish.run();
        }
        catch (ClientGoneException e)
        {
            // 写回浏览器失败：前端点了停止或关了页面
            log.debug("[AI] 写回前端失败，判定为前端已断开: {}", e.getCause() == null ? "" : e.getCause().getMessage());
            cancelUpstream.run();
        }
        catch (Exception e)
        {
            if (cancelled.get())
            {
                // 是取消导致的读失败，不算错误
                log.debug("[AI] 读取上游被中断（取消导致）");
                return;
            }
            log.error("[AI] 转发百炼失败，url={}", url, e);
            sendError(emitter, describeFailure(e));
            finish.run();
        }
    }

    /** 把底层异常翻译成用户能看懂的一句话，同时保留原始类型便于排查 */
    private String describeFailure(Exception e)
    {
        String name = e.getClass().getSimpleName();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("connect timed out") || msg.contains("Connection refused"))
        {
            return "连不上百炼（TCP 连接失败）。若本机 IPv6 不可达而域名有 AAAA 记录，"
                    + "JDK 不会自动回退 IPv4，需加 JVM 参数 -Djava.net.preferIPv4Stack=true";
        }
        if (name.contains("Timeout") || msg.contains("timed out"))
        {
            return "等待模型响应超时（" + properties.getTimeoutSeconds() + "s），模型可能正在排队，稍后重试";
        }
        if (msg.contains("Connection reset") || msg.contains("closed") || msg.contains("aborted"))
        {
            return "与百炼的连接被中断。若你刚点过「停止」，稍等几秒再试；反复出现请检查该 workspace 的并发配额";
        }
        if (msg.contains("401") || msg.contains("Unauthorized"))
        {
            return "百炼鉴权失败，检查 ai-local.yml 里的 api-key 是否有效";
        }
        return "调用失败：" + (msg.isEmpty() ? name : msg);
    }

    private String buildBody(AiChatRequest request) throws Exception
    {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("model", request.getModel() != null && !request.getModel().isBlank() ? request.getModel() : properties.getModel());
        node.put("stream", true);
        node.set("messages", objectMapper.valueToTree(
                request.getMessages() == null ? java.util.List.of() : request.getMessages()));
        if (request.getTools() != null && !request.getTools().isEmpty())
        {
            node.set("tools", objectMapper.valueToTree(request.getTools()));
        }
        return objectMapper.writeValueAsString(node);
    }

    private String readSafely(InputStream in)
    {
        try (InputStream stream = in)
        {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            return "（读取错误内容失败：" + e.getMessage() + "）";
        }
    }

    private static void closeQuietly(InputStream in)
    {
        if (in == null)
        {
            return;
        }
        try
        {
            in.close();
        }
        catch (IOException ignored)
        {
            // 关闭失败无需处理
        }
    }

    private void sendError(SseEmitter emitter, String message)
    {
        try
        {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("message", message);
            emitter.send(SseEmitter.event().name("error").data(objectMapper.writeValueAsString(node)));
        }
        catch (Exception ignored)
        {
            // 连接已断，忽略
        }
    }

    private static String trimTrailingSlash(String url)
    {
        if (url == null)
        {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** 把百炼的 HTTP 错误体翻译成能直接照做的提示 */
    private static String describeHttpError(int status, String detail)
    {
        if (detail.contains("BudgetLimitExceeded"))
        {
            return "百炼预算已用尽：请到百炼控制台『预算管理』提高或重置预算后再试";
        }
        if (detail.contains("AccessDenied.Unpurchased") || detail.contains("AccessDenied"))
        {
            return "当前账号未开通该模型：" + extractMessage(detail);
        }
        if (detail.contains("model_not_found") || detail.contains("Model not exist"))
        {
            return "模型不存在，请检查 application.yml 里的 ai.assistant.model";
        }
        if (detail.contains("Arrearage") || detail.contains("欠费"))
        {
            return "百炼账号欠费，请先充值";
        }
        return "百炼返回 HTTP " + status + "：" + extractMessage(detail);
    }

    /** 从 {"error":{"message":"..."}} 里取 message，取不到就原样返回 */
    private static String extractMessage(String detail)
    {
        int idx = detail.indexOf("\"message\":\"");
        if (idx < 0)
        {
            return detail;
        }
        int start = idx + 11;
        int end = detail.indexOf('"', start);
        return end > start ? detail.substring(start, end) : detail;
    }

    /** 只对「连接阶段就失败、还没产生任何输出」的错误重试 */
    private static boolean isRetryable(Exception e)
    {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("header parser received no bytes")
                || msg.contains("Connection reset")
                || msg.contains("Connection refused")
                || msg.contains("closed");
    }

    /**
     * 写回浏览器。失败说明前端已经断开 —— 与「上游调用失败」区分开，
     * 否则会被当成百炼故障，取消逻辑永远不会执行。
     * 注意：Windows 下 Tomcat 抛的是 IOException(WSAECONNABORTED)，不是 IllegalStateException。
     */
    private static void sendEvent(SseEmitter emitter, String event, String data)
    {
        try
        {
            emitter.send(SseEmitter.event().name(event).data(data));
        }
        catch (Exception e)
        {
            throw new ClientGoneException(e);
        }
    }

    /** 前端已断开 */
    private static class ClientGoneException extends RuntimeException
    {
        ClientGoneException(Throwable cause)
        {
            super(cause);
        }
    }
}
