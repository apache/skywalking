# Plugin Development Guide
This document describes how to understand, develop and contribute a plugin. 

There are 2 kinds of plugin:
1. [Tracing plugin](#tracing-plugin). Follow the distributed tracing concept to collect spans with tags and logs.
1. [Meter plugin](#meter-plugin). Collect numeric metrics in Counter, Gauge, and Histogram formats.

We also provide the [plugin test tool](#plugin-test-tool) to verify the data collected and reported by the plugin. If you plan to contribute any plugin to our main repo, the data would be verified by this tool too.

# Tracing plugin
## Concepts
### Span
The span is an important and recognized concept in the distributed tracing system. Learn about the **span** from the
[Google Dapper Paper](https://research.google.com/pubs/pub36356.html)  and
[OpenTracing](http://opentracing.io)

SkyWalking has supported OpenTracing and OpenTracing-Java API since 2017. Our concepts of the span are similar to that of the Google Dapper Paper and OpenTracing. We have also extended the span.

There are three types of span:

1.1 EntrySpan
The EntrySpan represents a service provider. It is also an endpoint on the server end. As an APM system, our target is the 
application servers. Therefore, almost all the services and MQ-consumers are EntrySpan.

1.2 LocalSpan
The LocalSpan represents a normal Java method that does not concern remote services. It is neither a MQ producer/consumer
nor a service (e.g. HTTP service) provider/consumer.

1.3 ExitSpan
The ExitSpan represents a client of service or MQ-producer. It is named the `LeafSpan` in the early versions of SkyWalking.
For example, accessing DB through JDBC and reading Redis/Memcached are classified as an ExitSpan. 

### ContextCarrier
In order to implement distributed tracing, cross-process tracing has to be bound, and the context must propagate 
across the process. This is where the ContextCarrier comes in.

Here are the steps on how to use the **ContextCarrier** in an `A->B` distributed call.
1. Create a new and empty `ContextCarrier` on the client end.
1. Create an ExitSpan by `ContextManager#createExitSpan` or use `ContextManager#inject` to initalize the `ContextCarrier`.
1. Place all items of `ContextCarrier` into heads (e.g. HTTP HEAD), attachments (e.g. Dubbo RPC framework) or messages (e.g. Kafka).
1. The `ContextCarrier` propagates to the server end through the service call.
1. On the server end, obtain all items from the heads, attachments or messages.
1. Create an EntrySpan by `ContextManager#createEntrySpan` or use `ContextManager#extract` to bind the client and server ends.


See the following examples, where we use the Apache HTTPComponent client plugin and Tomcat 7 server plugin:
1. Using the Apache HTTPComponent client plugin on the client end
```java
            span = ContextManager.createExitSpan("/span/operation/name", contextCarrier, "ip:port");
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                httpRequest.setHeader(next.getHeadKey(), next.getHeadValue());
            }
```

2. Using the Tomcat 7 server plugin on the server end
```java
            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(request.getHeader(next.getHeadKey()));
            }

            span = ContextManager.createEntrySpan(“/span/operation/name”, contextCarrier);
```

### ContextSnapshot
Besides cross-process tracing, cross-thread tracing has to be supported as well. For instance, both async process (in-memory MQ) 
and batch process are common in Java. Cross-process and cross-thread tracing are very similar in that they both require propagating
context, except that cross-thread tracing does not require serialization.

Here are the three steps on cross-thread propagation:
1. Use `ContextManager#capture` to get the ContextSnapshot object.
1. Let the sub-thread access the ContextSnapshot through method arguments or being carried by existing arguments
1. Use `ContextManager#continued` in sub-thread.

## Core APIs
### ContextManager
ContextManager provides all major and primary APIs.

1. Create EntrySpan
```java
public static AbstractSpan createEntrySpan(String endpointName, ContextCarrier carrier)
```
Create EntrySpan according to the operation name (e.g. service name, uri) and **ContextCarrier**.

2. Create LocalSpan
```java
public static AbstractSpan createLocalSpan(String endpointName)
```
Create LocalSpan according to the operation name (e.g. full method signature).

3. Create ExitSpan
```java
public static AbstractSpan createExitSpan(String endpointName, ContextCarrier carrier, String remotePeer)
```
Create ExitSpan according to the operation name (e.g. service name, uri) and the new **ContextCarrier** and peer address 
(e.g. ip+port, hostname+port).

### AbstractSpan
```java
    /**
     * Set the component id, which defines in {@link ComponentsDefine}
     *
     * @param component
     * @return the span for chaining.
     */
    AbstractSpan setComponent(Component component);

    AbstractSpan setLayer(SpanLayer layer);

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan tag(String key, String value);

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    AbstractSpan log(Throwable t);

    AbstractSpan errorOccurred();

    /**
     * Record an event at a specific timestamp.
     *
     * @param timestamp The explicit timestamp for the log record.
     * @param event the events
     * @return the Span, for chaining
     */
    AbstractSpan log(long timestamp, Map<String, ?> event);

    /**
     * Sets the string name for the logical operation this span represents.
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan setOperationName(String endpointName);
```
Besides setting the operation name, tags and logs, two attributes must be set, namely the component and layer. This is especially important for the EntrySpan and ExitSpan.

SpanLayer is the type of span. There are 5 values:
1. UNKNOWN (default)
1. DB
1. RPC_FRAMEWORK (designed for the RPC framework, rather than an ordinary HTTP call)
1. HTTP
1. MQ

Component IDs are defined and reserved by the SkyWalking project.
For extension of the component name/ID, please follow the [component library definitions and extensions](Component-library-settings.md) document.

### Special Span Tags
All tags are available in the trace view. Meanwhile, in the OAP backend analysis, some special tags or tag combinations provide other advanced features.

#### Tag key `status_code`
The value should be an integer. The response code of OAL entities corresponds to this value.

#### Tag keys `db.statement` and `db.type`.
The value of `db.statement` should be a string that represents the database statement, such as SQL, or `[No statement]/`+span#operationName if the value is empty.
When the exit span contains this tag, OAP samples the slow statements based on `agent-analyzer/default/maxSlowSQLLength`.
The threshold of slow statement is defined in accordance with [`agent-analyzer/default/slowDBAccessThreshold`](../setup/backend/slow-db-statement.md)

#### Extension logic endpoint: Tag key `x-le`
The logic endpoint is a concept that doesn't represent a real RPC call, but requires the statistic.
The value of `x-le` should be in JSON format. There are two options:
1. Define a separated logic endpoint. Provide its own endpoint name, latency and status. Suitable for entry and local span.
```json
{
  "name": "GraphQL-service",
  "latency": 100,
  "status": true
}
```
2. Declare the current local span representing a logic endpoint.
```json
{
  "logic-span": true
}
``` 

### Advanced APIs
#### Async Span APIs
There is a set of advanced APIs in Span which is specifically designed for async use cases. When tags, logs, and attributes (including end time) of the span need to be set in another thread, you should use these APIs.

```java
    /**
     * The span finish at current tracing context, but the current span is still alive, until {@link #asyncFinish}
     * called.
     *
     * This method must be called<br/>
     * 1. In original thread(tracing context).
     * 2. Current span is active span.
     *
     * During alive, tags, logs and attributes of the span could be changed, in any thread.
     *
     * The execution times of {@link #prepareForAsync} and {@link #asyncFinish()} must match.
     *
     * @return the current span
     */
    AbstractSpan prepareForAsync();

    /**
     * Notify the span, it could be finished.
     *
     * The execution times of {@link #prepareForAsync} and {@link #asyncFinish()} must match.
     *
     * @return the current span
     */
    AbstractSpan asyncFinish();
```
1. Call `#prepareForAsync` in the original context.
1. Run `ContextManager#stopSpan` in the original context when your job in the current thread is complete.
1. Propagate the span to any other thread.
1. Once the above steps are all set, call `#asyncFinish` in any thread.
1. When `#prepareForAsync` is complete for all spans, the tracing context will be finished and will report to the backend (based on the count of API execution).

## Develop a plugin
### Abstract
The basic method to trace is to intercept a Java method, by using byte code manipulation tech and AOP concept.
SkyWalking has packaged the byte code manipulation tech and tracing context propagation,
so you simply have to define the intercept point (a.k.a. aspect pointcut in Spring).

### Intercept
SkyWalking provides two common definitions to intercept constructor, instance method and class method.

#### v1 APIs
* Extend `ClassInstanceMethodsEnhancePluginDefine` to define `constructor` intercept points and `instance method` intercept points.
* Extend `ClassStaticMethodsEnhancePluginDefine` to define `class method` intercept points.

Of course, you can extend `ClassEnhancePluginDefine` to set all intercept points, although it is uncommon to do so.

#### v2 APIs
v2 APIs provide an enhanced interceptor, which could propagate context through MIC(MethodInvocationContext).

* Extend `ClassInstanceMethodsEnhancePluginDefineV2` to define `constructor` intercept points and `instance method` intercept points.
* Extend `ClassStaticMethodsEnhancePluginDefineV2` to define `class method` intercept points.

Of course, you can extend `ClassEnhancePluginDefineV2` to set all intercept points, although it is uncommon to do so.


### Implement plugin
See the following demonstration on how to implement a plugin by extending `ClassInstanceMethodsEnhancePluginDefine`.

1. Define the target class name.
```java
protected abstract ClassMatch enhanceClass();
```

ClassMatch represents how to match the target classes. There are 4 ways:
* `byName`: Based on the full class names (package name + `.` + class name).
* `byClassAnnotationMatch`: Depends on whether there are certain annotations in the target classes.
* `byMethodAnnotationMatch`: Depends on whether there are certain annotations in the methods of the target classes.
* `byHierarchyMatch`: Based on the parent classes or interfaces of the target classes.

**Attention**:
* Never use `ThirdPartyClass.class` in the instrumentation definitions, such as `takesArguments(ThirdPartyClass.class)`, or `byName(ThirdPartyClass.class.getName())`, because of the fact that `ThirdPartyClass` dose not necessarily exist in the target application and this will break the agent; we have `import` checks to assist in checking this in CI, but it doesn't cover all scenarios of this limitation, so never try to work around this limitation by something like using full-qualified-class-name (FQCN), i.e. `takesArguments(full.qualified.ThirdPartyClass.class)` and `byName(full.qualified.ThirdPartyClass.class.getName())` will pass the CI check, but are still invalid in the agent codes. Therefore, **Use Full Qualified Class Name String Literature Instead**.
* Even if you are perfectly sure that the class to be intercepted exists in the target application (such as JDK classes), still, do not use `*.class.getName()` to get the class String name. We recommend you to use a literal string. This is to avoid ClassLoader issues.
* `by*AnnotationMatch` does not support inherited annotations.
* We do not recommend using `byHierarchyMatch` unless necessary. Using it may trigger the interception of
many unexcepted methods, which would cause performance issues.

Example：
```java
@Override
protected ClassMatch enhanceClassName() {
    return byName("org.apache.catalina.core.StandardEngineValve");		
}		      

```

2. Define an instance method intercept point.
```java
public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();

public interface InstanceMethodsInterceptPoint {
    /**
     * class instance methods matcher.
     *
     * @return methods matcher
     */
    ElementMatcher<MethodDescription> getMethodsMatcher();

    /**
     * @return represents a class name, the class instance must instanceof InstanceMethodsAroundInterceptor.
     */
    String getMethodsInterceptor();

    boolean isOverrideArgs();
}
```
You may also use `Matcher` to set the target methods. Return **true** in `isOverrideArgs`, if you want to change the argument
ref in interceptor.

The following sections will tell you how to implement the interceptor.

3. Add plugin definition into the `skywalking-plugin.def` file.
```properties
tomcat-7.x/8.x=TomcatInstrumentation
```

4. Set up `witnessClasses` and/or `witnessMethods` if the instrumentation has to be activated in specific versions.

   Example:

   ```java
   // The plugin is activated only when the foo.Bar class exists.
   @Override
   protected String[] witnessClasses() {
     return new String[] {
       "foo.Bar"
     };
   }
   
   // The plugin is activated only when the foo.Bar#hello method exists.
   @Override
   protected List<WitnessMethod> witnessMethods() {
     List<WitnessMethod> witnessMethodList = new ArrayList<>();
     WitnessMethod witnessMethod = new WitnessMethod("foo.Bar", ElementMatchers.named("hello"));
     witnessMethodList.add(witnessMethod);
     return witnessMethodList;
   }
   ```
   For more examples, see [WitnessTest.java](../../../apm-sniffer/apm-agent-core/src/test/java/org/apache/skywalking/apm/agent/core/plugin/witness/WitnessTest.java)

   

### Implement an interceptor
As an interceptor for an instance method, it has to implement 
`org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor`
```java
/**
 * A interceptor, which intercept method's invocation. The target methods will be defined in {@link
 * ClassEnhancePluginDefine}'s subclass, most likely in {@link ClassInstanceMethodsEnhancePluginDefine}
*/
public interface InstanceMethodsAroundInterceptor {
    /**
     * called before target method invocation.
     *
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable;

    /**
     * called after target method invocation. Even method's invocation triggers an exception.
     *
     * @param ret the method's original return value.
     * @return the method's actual return value.
     * @throws Throwable
     */
    Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable;

    /**
     * called when occur exception.
     *
     * @param t the exception occur.
     */
    void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Throwable t);
}
```
Use the core APIs before and after calling the method, as well as during exception handling.


#### V2 APIs
The interceptor of V2 API uses `MethodInvocationContext context` to replace the `MethodInterceptResult result` in the `beforeMethod`,
and be added as a new parameter in `afterMethod` and `handleMethodException`.

`MethodInvocationContext context` is only shared in one time execution, and safe to use when face concurrency execution.

```java
/**
 * A v2 interceptor, which intercept method's invocation. The target methods will be defined in {@link
 * ClassEnhancePluginDefineV2}'s subclass, most likely in {@link ClassInstanceMethodsEnhancePluginDefine}
 */
public interface InstanceMethodsAroundInterceptorV2 {
    /**
     * called before target method invocation.
     *
     * @param context the method invocation context including result context.
     */
    void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                      MethodInvocationContext context) throws Throwable;

    /**
     * called after target method invocation. Even method's invocation triggers an exception.
     *
     * @param ret the method's original return value. May be null if the method triggers an exception.
     * @return the method's actual return value.
     */
    Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                       Object ret, MethodInvocationContext context) throws Throwable;

    /**
     * called when occur exception.
     *
     * @param t the exception occur.
     */
    void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                               Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context);

}
```

### Bootstrap class instrumentation.
SkyWalking has packaged the bootstrap instrumentation in the agent core. You can easily implement it by declaring it in the instrumentation definition.

Override the `public boolean isBootstrapInstrumentation()` and return **true**. Such as
```java
public class URLInstrumentation extends ClassEnhancePluginDefine {
    private static String CLASS_NAME = "java.net.URL";

    @Override protected ClassMatch enhanceClass() {
        return byName(CLASS_NAME);
    }

    @Override public ConstructorInterceptPoint[] getConstructorsInterceptPoints() {
        return new ConstructorInterceptPoint[] {
            new ConstructorInterceptPoint() {
                @Override public ElementMatcher<MethodDescription> getConstructorMatcher() {
                    return any();
                }

                @Override public String getConstructorInterceptor() {
                    return "org.apache.skywalking.apm.plugin.jre.httpurlconnection.Interceptor2";
                }
            }
        };
    }

    @Override public InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints() {
        return new InstanceMethodsInterceptPoint[0];
    }

    @Override public StaticMethodsInterceptPoint[] getStaticMethodsInterceptPoints() {
        return new StaticMethodsInterceptPoint[0];
    }

    @Override public boolean isBootstrapInstrumentation() {
        return true;
    }
}
```

`ClassEnhancePluginDefineV2` is provided in v2 APIs, `#isBootstrapInstrumentation` works too.

**NOTE**: Bootstrap instrumentation should be used only where necessary. During its actual execution, it mostly affects the JRE core(rt.jar). Defining it other than where necessary could lead to unexpected results or side effects.

### Provide custom config for the plugin
The config could provide different behaviours based on the configurations. The SkyWalking plugin mechanism provides the configuration
injection and initialization system in the agent core.

Every plugin could declare one or more classes to represent the config by using `@PluginConfig` annotation. The agent core
could initialize this class' static field through System environments, System properties, and `agent.config` static file.

The `#root()` method in the `@PluginConfig` annotation requires declaring the root class for the initialization process.
Typically, SkyWalking prefers to use nested inner static classes for the hierarchy of the configuration. 
We recommend using `Plugin`/`plugin-name`/`config-key` as the nested classes structure of the config class.

**NOTE**: because of the Java ClassLoader mechanism, the `@PluginConfig` annotation should be added on the real class used in the interceptor codes. 

In the following example, `@PluginConfig(root = SpringMVCPluginConfig.class)` indicates that initialization should 
start with using `SpringMVCPluginConfig` as the root. Then, the config key of the attribute `USE_QUALIFIED_NAME_AS_ENDPOINT_NAME`
should be `plugin.springmvc.use_qualified_name_as_endpoint_name`.
```java
public class SpringMVCPluginConfig {
    public static class Plugin {
        // NOTE, if move this annotation on the `Plugin` or `SpringMVCPluginConfig` class, it no longer has any effect. 
        @PluginConfig(root = SpringMVCPluginConfig.class)
        public static class SpringMVC {
            /**
             * If true, the fully qualified method name will be used as the endpoint name instead of the request URL,
             * default is false.
             */
            public static boolean USE_QUALIFIED_NAME_AS_ENDPOINT_NAME = false;

            /**
             * This config item controls that whether the SpringMVC plugin should collect the parameters of the
             * request.
             */
            public static boolean COLLECT_HTTP_PARAMS = false;
        }

        @PluginConfig(root = SpringMVCPluginConfig.class)
        public static class Http {
            /**
             * When either {@link Plugin.SpringMVC#COLLECT_HTTP_PARAMS} is enabled, how many characters to keep and send
             * to the OAP backend, use negative values to keep and send the complete parameters, NB. this config item is
             * added for the sake of performance
             */
            public static int HTTP_PARAMS_LENGTH_THRESHOLD = 1024;
        }
    }
}
```


# Meter Plugin
Java agent plugin could use meter APIs to collect metrics for backend analysis.

* `Counter` API represents a single monotonically increasing counter which automatically collects data and reports to the backend.
```java
import org.apache.skywalking.apm.agent.core.meter.MeterFactory;

Counter counter = MeterFactory.counter(meterName).tag("tagKey", "tagValue").mode(Counter.Mode.INCREMENT).build();
counter.increment(1d);
```
1. `MeterFactory.counter` creates a new counter builder with the meter name.
1. `Counter.Builder.tag(String key, String value)` marks a tag key/value pair.
1. `Counter.Builder.mode(Counter.Mode mode)` changes the counter mode. `RATE` mode means the reporting rate to the backend.
1. `Counter.Builder.build()` builds a new `Counter` which is collected and reported to the backend.
1. `Counter.increment(double count)` increment counts to the `Counter`. It could be a positive value.

* `Gauge` API represents a single numerical value.
```java
import org.apache.skywalking.apm.agent.core.meter.MeterFactory;

ThreadPoolExecutor threadPool = ...;
Gauge gauge = MeterFactory.gauge(meterName, () -> threadPool.getActiveCount()).tag("tagKey", "tagValue").build();
```
1. `MeterFactory.gauge(String name, Supplier<Double> getter)` creates a new gauge builder with the meter name and supplier function. This function must return a `double` value.
1. `Gauge.Builder.tag(String key, String value)` marks a tag key/value pair.
1. `Gauge.Builder.build()` builds a new `Gauge` which is collected and reported to the backend.

* `Histogram` API represents a summary sample observations with customized buckets.
```java
import org.apache.skywalking.apm.agent.core.meter.MeterFactory;

Histogram histogram = MeterFactory.histogram("test").tag("tagKey", "tagValue").steps(Arrays.asList(1, 5, 10)).minValue(0).build();
histogram.addValue(3);
```
1. `MeterFactory.histogram(String name)` creates a new histogram builder with the meter name.
1. `Histogram.Builder.tag(String key, String value)` marks a tag key/value pair.
1. `Histogram.Builder.steps(List<Double> steps)` sets up the max values of every histogram buckets.
1. `Histogram.Builder.minValue(double value)` sets up the minimal value of this histogram. Default is `0`.
1. `Histogram.Builder.build()` builds a new `Histogram` which is collected and reported to the backend.
1. `Histogram.addValue(double value)` adds value into the histogram, and automatically analyzes what bucket count needs to be incremented. Rule: count into [step1, step2).

# Plugin Test Tool
The [Apache SkyWalking Agent Test Tool Suite](https://github.com/apache/skywalking-agent-test-tool) is an incredibly useful test tool suite that is available in a wide variety of agent languages. It includes the mock collector and validator. The mock collector is a SkyWalking receiver, like the OAP server.

You could learn how to use this tool to test the plugin in [this doc](Plugin-test.md). This is a must if you want to contribute plugins to the SkyWalking official repo.

# Contribute plugins to the Apache SkyWalking repository
We welcome everyone to contribute their plugins.

Please follow these steps:
1. Submit an issue for your plugin, including any supported versions.
1. Create sub modules under `apm-sniffer/apm-sdk-plugin` or `apm-sniffer/optional-plugins`, and the name should include supported library name and versions.
1. Follow this guide to develop. Make sure comments and test cases are provided.
1. Develop and test.
1. Provide the automatic test cases. Learn `how to write the plugin test case` from this [doc](Plugin-test.md)
1. Send a pull request and ask for review. 
1. The plugin committers will approve your plugins, plugin CI-with-IT, e2e, and the plugin tests will be passed.
1. The plugin is accepted by SkyWalking. 
