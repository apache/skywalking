# 如何追踪web应用服务器访问？ 
- 引入所需插件
```xml
<!-- web，监控web调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-web-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 服务端使用Filter追踪web请求
```xml
<filter>
    <filter-name>tracingFilter</filter-name>
    <filter-class>com.ai.cloud.skywalking.plugin.web.SkyWalkingFilter</filter-class>
    <init-param>
        <param-name>tracing-name</param-name>
        <!--分布式埋点信息，默认放在request的header中，key=SkyWalking-TRACING-NAME,可根据需要修改-->
        <param-value>SkyWalking-TRACING-NAME</param-value>
    </init-param>
</filter>
<filter-mapping>
    <filter-name>tracingFilter</filter-name>
    <!--追踪路径应为MVC的请求路径，不建议包括js/css/图片等资源路径-->
    <url-pattern>/request-uri</url-pattern>
</filter-mapping>
```
