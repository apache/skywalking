# 如何追踪HttpClient发起HTTP调用？

## httpclient 4.2.x
- 引入所需插件
```xml
<!-- httpClient插件，监控httpClient 4.2的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-httpClient-4.2.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 使用SWTracingHttpClient封装所需的httpClient，此httpClient所有调用都会被监控
```java
HttpClient httpclient = new SWTracingHttpClient(new DefaultHttpClient());
```
- 上下文将被存储在http request head，中，默认名称和SkyWalkingFilter保持一致。
- 如果服务端使用dubbox 2.8.4 的提供的http-rest，请使用方法重载
```java
HttpClient httpclient = new SWTracingHttpClient(new DefaultHttpClient(), "Dubbo-Attachments");
```