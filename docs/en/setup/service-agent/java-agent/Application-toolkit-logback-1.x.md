# logback plugin
* Dependency the toolkit, such as using maven or gradle
```xml
    <dependency>
         <groupId>org.apache.skywalking</groupId>
         <artifactId>apm-toolkit-logback-1.x</artifactId>
         <version>{project.release.version}</version>
     </dependency>
```

* set `%tid` in `Pattern` section of logback.xml
```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout">
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] [%thread] %-5level %logger{36} -%msg%n</Pattern>
            </layout>
        </encoder>
    </appender>
```

* with the MDC, set `%X{tid}` in `Pattern` section of logback.xml
```xml
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
            <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout">
                <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{tid}] [%thread] %-5level %logger{36} -%msg%n</Pattern>
            </layout>
        </encoder>
    </appender>
```


* Support logback AsyncAppender(MDC also support), No additional configuration is required. Refer to the demo of logback.xml below. For details: [Logback AsyncAppender](https://logback.qos.ch/manual/appenders.html#AsyncAppender)
```xml
    <configuration scan="true" scanPeriod=" 5 seconds">
        <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.mdc.TraceIdMDCPatternLogbackLayout">
                    <Pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%X{tid}] [%thread] %-5level %logger{36} -%msg%n</Pattern>
                </layout>
            </encoder>
        </appender>
    
        <appender name="ASYNC" class="ch.qos.logback.classic.AsyncAppender">
            <discardingThreshold>0</discardingThreshold>
            <queueSize>1024</queueSize>
            <neverBlock>true</neverBlock>
            <appender-ref ref="STDOUT"/>
        </appender>
    
        <root level="INFO">
            <appender-ref ref="ASYNC"/>
        </root>
    </configuration>
```

* When you use `-javaagent` to active the sky-walking tracer, logback will output **traceId**, if it existed. If the tracer is inactive, the output will be `TID: N/A`.

# logstash logback plugin

## 1.user guide

- Dependency the toolkit, such as using maven or gradle

```xml
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>${skywalking.version}</version>
</dependency>
```

- set `LogstashEncoder` of logback.xml

```xml
<encoder charset="UTF-8" class="net.logstash.logback.encoder.LogstashEncoder">
    <provider class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.logstash.TraceIdJsonProvider">
    </provider>
    <customFields>{"app_id":"${app_id}"}</customFields>
</encoder>
```



## 2.why it works

- extension of logstash-logback-encoder provider, [official guide](https://github.com/logstash/logstash-logback-encoder#custom-json-provider)
- Custom LogstashEncoder JSON Provider like this:

```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    ...
    <provider class="your.provider.YourJsonProvider">
        <!-- Any properties exposed by your provider can be set here -->
    </provider>
    ...
</encoder>
```

- TraceIdJsonProvider analyze

```java
public class TraceIdJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> implements FieldNamesAware<LogstashFieldNames> {

    public static final String TRACING_ID = "TID";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        Map<String, String> map = event.getLoggerContextVO().getPropertyMap();
        JsonWritingUtils.writeStringField(generator, getFieldName(), map.get(TRACING_ID));
    }

    @Override
    public void setFieldNames(LogstashFieldNames fieldNames) {
        setFieldName(TRACING_ID);
    }
}
```

1. the setFieldNames method will add a json field named `TID`
2. the writeTo method will set the `TID` value, for `propertyMap` is always not null, it at least contains `destination` of logstash, so the code will not throw `NPE`

- TcpSocketAppenderActivation analyze

```java
public class TcpSocketAppenderActivation extends ClassInstanceMethodsEnhancePluginDefine {

    public static final String INTERCEPT_CLASS = "org.apache.skywalking.apm.toolkit.activation.log.logback.v1.x.logstash.TcpSocketAppenderInterceptor";
    public static final String ENHANCE_CLASS = "net.logstash.logback.appender.LogstashTcpSocketAppender";
    public static final String ENHANCE_METHOD = "prepareForDeferredProcessing";

    /**
     * @return the target class, which needs active.
     */
    @Override
    protected ClassMatch enhanceClass() {
        return byName(ENHANCE_CLASS);
    }

    /**
     * @return null, no need to intercept constructor of enhance class.
     */
    @Override
    public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return null;
    }

    /**
     * @return the collection of {@link StaticMethodsInterceptPoint}, represent the intercepted methods and their
     * interceptors.
     */
    @Override
    public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[] {
            new InstanceMethodsInterceptPoint() {
                @Override
                public ElementMatcher<MethodDescription> getMethodsMatcher() {
                    return named(ENHANCE_METHOD).and(takesArgumentWithType(0, "ch.qos.logback.classic.spi.ILoggingEvent"));
                }

                @Override
                public String getMethodsInterceptor() {
                    return INTERCEPT_CLASS;
                }

                @Override public boolean isOverrideArgs() {
                    return false;
                }
            }
        };
    }
}

```

1. net.logstash.logback.appender.`LogstashTcpSocketAppender` is logstash appender, contains a method:

```java
    @Override
    protected void prepareForDeferredProcessing(final ILoggingEvent event) {
        super.prepareForDeferredProcessing(event);
        if (includeCallerData) {
            event.getCallerData();
        }
    }
```

2. so we enhance it by the `beforeMethod` of `TcpSocketAppenderInterceptor` :

```java
    @Override public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ILoggingEvent event = (ILoggingEvent)allArguments[0];
        if (event != null && event.getLoggerContextVO() != null && event.getLoggerContextVO().getPropertyMap() != null) {
            event.getLoggerContextVO().getPropertyMap().put("TID", ContextManager.getGlobalTraceId());
        }
    }
```

3. it put a key value of `(TID, ContextManager.getGlobalTraceId())` to the `ILoggingEvent`, so we can get it in the `writeTo` method of `TraceIdJsonProvider` 
4. the method `prepareForDeferredProcessing` of `LogstashTcpSocketAppender` works on the worker thread, but others works on the logstash thread, because `logstash-logback-encoder` if a async plugin, so we enhance it by this way

## 3.related pictures

- ConsoleAppender plugin

![image-20190829093550854](/Users/wuxingye/Library/Application Support/typora-user-images/image-20190829093550854.png)

- logstash logback plugin

![image-20190829093901508](/Users/wuxingye/Library/Application Support/typora-user-images/image-20190829093901508.png)

- skywalking ui

![image-20190829094040515](/Users/wuxingye/Library/Application Support/typora-user-images/image-20190829094040515.png)

- before add `TID`

![image-20190829094937493](/Users/wuxingye/Library/Application Support/typora-user-images/image-20190829094937493.png)

- after add `TID`

![image-20190829095011656](/Users/wuxingye/Library/Application Support/typora-user-images/image-20190829095011656.png)