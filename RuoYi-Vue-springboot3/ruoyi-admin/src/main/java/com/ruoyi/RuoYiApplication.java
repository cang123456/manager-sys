package com.ruoyi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class RuoYiApplication
{
    public static void main(String[] args)
    {
        // 百炼域名同时有 AAAA 记录，本机 IPv6 不可达时 JDK 会一直等到 connect 超时
        // （curl 有 Happy Eyeballs 自动回退 IPv4，JDK 17 没有），表现为
        // java.net.ConnectException: HTTP connect timed out。
        // 这里强制走 IPv4 规避；若将来必须用 IPv6，删掉这一行即可。
        System.setProperty("java.net.preferIPv4Stack", "true");

        // JDK 17 在 Windows 上默认编码是 GBK，LangChain4j 的 HTTP 客户端按默认编码
        // 解码大模型的流式响应，中文会变成「瑙掕壊绠＄悊」这种乱码。
        // 必须在任何 Charset 初始化之前设置，所以放在 main 第一行附近。
        System.setProperty("file.encoding", "UTF-8");
        if (!"UTF-8".equalsIgnoreCase(java.nio.charset.Charset.defaultCharset().name()))
        {
            System.err.println("[WARN] 当前 JVM 默认编码为 " + java.nio.charset.Charset.defaultCharset()
                    + "，中文可能乱码；请在启动参数中加 -Dfile.encoding=UTF-8");
        }

        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(RuoYiApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  若依启动成功   ლ(´ڡ`ლ)ﾞ  \n" +
                " .-------.       ____     __        \n" +
                " |  _ _   \\      \\   \\   /  /    \n" +
                " | ( ' )  |       \\  _. /  '       \n" +
                " |(_ o _) /        _( )_ .'         \n" +
                " | (_,_).' __  ___(_ o _)'          \n" +
                " |  |\\ \\  |  ||   |(_,_)'         \n" +
                " |  | \\ `'   /|   `-'  /           \n" +
                " |  |  \\    /  \\      /           \n" +
                " ''-'   `'-'    `-..-'              ");
    }
}
