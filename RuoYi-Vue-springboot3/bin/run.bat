@echo off
echo.
echo [信息] 使用Jar命令运行Web工程。
echo.

cd %~dp0
cd ../ruoyi-admin/target

REM -Dfile.encoding=UTF-8 必须加：JDK 17 在 Windows 上默认 GBK，
REM LangChain4j 的 HTTP 客户端按默认编码解码大模型流式响应，中文会乱码。
set JAVA_OPTS=-Xms256m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8

java %JAVA_OPTS% -jar ruoyi-admin.jar

cd ../bin
pause
