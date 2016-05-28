## httpclient 4.3.x
- 引入所需插件
```xml
<!-- httpClient插件，监控httpClient 4.3的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-httpClient-4.3.x-plugin</artifactId>
    <version>{latest_version}</version>
</dependency>
```
- 使用SWTracingCloseableHttpClient封装所需的httpClient，此httpClient所有调用都会被监控
```java
CloseableHttpClient httpclient = new SWTracingCloseableHttpClient(closeableHttpClient);
```
- 上下文将被存储在http request head，中，默认名称和SkyWalkingFilter保持一致。
- 如果服务端使用dubbox 2.8.4 的提供的http-rest，请使用方法重载
```java
CloseableHttpClient httpclient = new SWTracingCloseableHttpClient(closeableHttpClient, "Dubbo-Attachments");
```
