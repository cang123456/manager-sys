package com.ruoyi.web.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 自己实现的 HTTP 客户端：把 LangChain4j 的底层传输换成 OkHttp
 *
 * 为什么要自己写：
 * 1. 框架自带的 JDK HttpClient 走 HTTP/2 时，服务端关闭连接（GOAWAY）会把
 *    同一条 TCP 上所有多路复用的流一起 cancel，表现为
 *    java.net.SocketException: Connection reset。日志里两个请求在同一毫秒
 *    同时报错，就是被"连坐"的证据。
 * 2. JDK HttpClient 不暴露应用层 PING，做不了心跳保活。
 * 3. LangChain4j 1.0.0 没有 OkHttp 适配模块（那是 1.20+ 才有的），
 *    但框架把 HttpClient 接口开放给我们注入，所以自己实现即可。
 *
 * OkHttp 带来的三件事：
 * - pingInterval：HTTP/2 下定期发 PING 帧，收不到 PONG 就判定连接已死，
 *   提前重建，而不是等到发请求时才发现
 * - connectionPool：空闲连接按时间回收，不会复用到"客户端以为活着、
 *   服务端早已关掉"的连接
 * - retryOnConnectionFailure：复用失败时自动换一条新连接重试
 *
 * SSE 的解析仍然交给框架的 ServerSentEventParser，这里只负责
 * "建立连接 → 拿到字节流 → 把流交给 parser"。
 *
 * @author ruoyi
 */
public class OkHttpSseHttpClient implements HttpClient
{
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient okHttpClient;

    private OkHttpSseHttpClient(OkHttpClient okHttpClient)
    {
        this.okHttpClient = okHttpClient;
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /** 非流式：同步请求，拿到完整响应体 */
    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException
    {
        Request okRequest = toOkRequest(request);
        try (Response response = okHttpClient.newCall(okRequest).execute())
        {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful())
            {
                throw new HttpException(response.code(), body);
            }
            return SuccessfulHttpResponse.builder()
                    .statusCode(response.code())
                    .headers(toHeaderMap(response.headers()))
                    .body(body)
                    .build();
        }
        catch (HttpException e)
        {
            throw e;
        }
        catch (IOException e)
        {
            throw new RuntimeException("请求百炼失败：" + e.getMessage(), e);
        }
    }

    /**
     * 流式：把字节流交给框架的 SSE 解析器
     *
     * 这里阻塞读，直到上游流结束或出错。出错一律交给 listener.onError，
     * 不往外抛 —— 这是框架约定的回调语义。
     */
    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener)
    {
        Request okRequest = toOkRequest(request);
        Response response = null;
        try
        {
            response = okHttpClient.newCall(okRequest).execute();

            int code = response.code();
            if (code < 200 || code >= 300)
            {
                String errorBody = response.body() == null ? "" : response.body().string();
                throw new HttpException(code, errorBody);
            }

            listener.onOpen(SuccessfulHttpResponse.builder()
                    .statusCode(code)
                    .headers(toHeaderMap(response.headers()))
                    .build());

            ResponseBody responseBody = response.body();
            if (responseBody == null)
            {
                throw new IllegalStateException("百炼返回了空的响应体");
            }
            InputStream stream = responseBody.byteStream();
            parser.parse(stream, listener);
            listener.onClose();
        }
        catch (Exception e)
        {
            listener.onError(e);
        }
        finally
        {
            if (response != null)
            {
                response.close();
            }
        }
    }

    private static Request toOkRequest(HttpRequest request)
    {
        String method = request.method().name();

        Request.Builder builder = new Request.Builder().url(request.url());
        for (Map.Entry<String, List<String>> entry : request.headers().entrySet())
        {
            for (String value : entry.getValue())
            {
                builder.addHeader(entry.getKey(), value);
            }
        }

        // GET / HEAD 不允许带 body，OkHttp 会直接抛错
        RequestBody body = null;
        if (!"GET".equals(method) && !"HEAD".equals(method) && request.body() != null)
        {
            body = RequestBody.create(request.body().getBytes(StandardCharsets.UTF_8), JSON);
        }
        builder.method(method, body);
        return builder.build();
    }

    private static Map<String, List<String>> toHeaderMap(Headers headers)
    {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String name : headers.names())
        {
            map.put(name, new ArrayList<>(headers.values(name)));
        }
        return map;
    }

    /** 连接层参数。默认值针对 SSE 长流场景调过。 */
    public static class Builder implements HttpClientBuilder
    {
        /** 建连超时：这条链路第一个 IP 常要 2 秒才失败，4 秒留足换路余量 */
        private Duration connectTimeout = Duration.ofSeconds(4);

        /** 读超时：两次读到数据之间的最长间隔。模型首 token 可能要等几秒 */
        private Duration readTimeout = Duration.ofMinutes(2);

        private Duration writeTimeout = Duration.ofSeconds(30);

        /** HTTP/2 PING 间隔。0 表示不发心跳 */
        private Duration pingInterval = Duration.ofSeconds(30);

        /** 空闲连接回收时间。比服务端网关的空闲超时更短，避免复用到已关闭的连接 */
        private Duration keepAlive = Duration.ofSeconds(30);

        private int maxIdleConnections = 5;

        /** 关闭后强制走 HTTP/1.1，用于对比排查 */
        private boolean http2Enabled = true;

        @Override
        public Duration connectTimeout()
        {
            return connectTimeout;
        }

        @Override
        public Builder connectTimeout(Duration connectTimeout)
        {
            this.connectTimeout = connectTimeout;
            return this;
        }

        @Override
        public Duration readTimeout()
        {
            return readTimeout;
        }

        @Override
        public Builder readTimeout(Duration readTimeout)
        {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder writeTimeout(Duration writeTimeout)
        {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public Builder pingInterval(Duration pingInterval)
        {
            this.pingInterval = pingInterval;
            return this;
        }

        public Builder keepAlive(Duration keepAlive)
        {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder maxIdleConnections(int maxIdleConnections)
        {
            this.maxIdleConnections = maxIdleConnections;
            return this;
        }

        public Builder http2(boolean http2Enabled)
        {
            this.http2Enabled = http2Enabled;
            return this;
        }

        @Override
        public OkHttpSseHttpClient build()
        {
            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .writeTimeout(writeTimeout.toMillis(), TimeUnit.MILLISECONDS)
                    .connectionPool(new ConnectionPool(maxIdleConnections, keepAlive.getSeconds(), TimeUnit.SECONDS))
                    // 复用失效连接时自动换一条新的重试（OkHttp 内建能力）
                    .retryOnConnectionFailure(true);

            if (http2Enabled)
            {
                if (pingInterval != null && pingInterval.getSeconds() > 0)
                {
                    // HTTP/2 专用：定期发 PING，20 秒内收不到 PONG 判定连接已死
                    builder.pingInterval(pingInterval.getSeconds(), TimeUnit.SECONDS);
                }
            }
            else
            {
                builder.protocols(List.of(Protocol.HTTP_1_1));
            }

            return new OkHttpSseHttpClient(builder.build());
        }
    }
}
