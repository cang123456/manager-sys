package com.ruoyi.web.ai;

/**
 * 会话记忆的唯一标识：用户 + 会话
 *
 * 之前只用 sessionId 作 key，而 sessionId 由前端生成、存在浏览器本地存储里，
 * 服务端无从校验归属。任何拿到别人 sessionId 的人都能读写对方的会话记忆
 * （/ai/chat 读、/ai/tool-result 写、/ai/reset 删）。
 *
 * 加上 userId 后，key 变成服务端可控的复合值：
 * - userId 来自 JWT，客户端改不了
 * - 攻击者拿自己的身份 + 别人的 sessionId 拼出来的是一条全新会话，
 *   既读不到数据，也测不出这个 sessionId 是否存在（不需要返回 403）
 *
 * 用 record 而不是拼字符串（userId + ":" + sessionId），是因为字符串拼接
 * 在 sessionId 含分隔符时能构造碰撞；record 的 equals/hashCode 天然可靠。
 *
 * @author ruoyi
 */
public record MemoryKey(Long userId, String sessionId)
{
    @Override
    public String toString()
    {
        return userId + ":" + sessionId;
    }
}
