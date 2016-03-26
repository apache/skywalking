## 通过扩展log4j或log4j2，在应用日志中，显示trace-id
### log4j
- 编译并发布skywalking-log/log4j-1.x-plugin
```xml
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-log4j-1.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 配置log4j配置文件
```properties
log4j.appender.A1.layout=com.ai.cloud.skywalking.plugin.log.log4j.v1.x.TraceIdPatternLayout
#%x为traceid的转义符
log4j.appender.A1.layout.ConversionPattern=[%x] %-d{yyyy-MM-dd HH:mm:ss.SSS} %c %n[%p] %n%m%n
```

### log4j2
- 编译并发布skywalking-log/log4j-2.x-plugin
- 引用所需的日志插件
```xml
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-log4j-2.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 配置log4j2配置文件
```xml
<!--%tid为traceid的转义符-->
<PatternLayout  pattern="%d{HH:mm:ss.SSS} [%tid] [%t] %-5level %logger{36} - %msg%n"/>
```

- 日志示例
```
#tid:N/A，代表环境设置不正确或监控已经关闭
#tid: ,代表测试当前访问不在监控范围
#tid:1.0a2.1453065000002.c3f8779.27878.30.184，标识此次访问的tid信息，示例如下
[DEBUG] Returning handler method [public org.springframework.web.servlet.ModelAndView com.ai.cloud.skywalking.example.controller.OrderSaveController.save(javax.servlet.http.HttpServletRequest)] TID:1.0a2.1453192613272.2e0c63e.11144.58.1 2016-01-19 16:36:53.288 org.springframework.beans.factory.support.DefaultListableBeanFactory 
```

## 如何在追踪日志中记录日志上下文
- 使用sky walking提供的专用API，可以将日志保存到追踪日志中。示例如下：
```java
String businessKey = "phoneNumber:" + phoneNumber + ",resourceId:" + resourceId + ",mail:" + mail;
BusinessKeyAppender.setBusinessKey2Trace(businessKey);
```

## 如何在代码中获取traceid
- 通过API获取traceid
```java
Tracing.getTraceId();
```

## 还有其他方式获取traceid么？
- 通过web应用的http调用入口，通过返回的header信息，找到此次调用的traceid。前提：此web应用的url，已经使用skywalking进行监控。

