## 插件开发指南
这边文档描述插件的开发和贡献方法

## 核心概念
### 一. Span
Span是追踪系统中的通用概念（有时候被翻译成埋点），关于Span的定义，请参考[OpenTracing 中文版](https://github.com/opentracing-contrib/opentracing-specification-zh/blob/master/specification.md#opentracing数据模型)。

SkyWalking作为OpenTracing的支持者，在核心实现中，与标准有较高的相似度。当然，作为实际产品的需要，我们一会扩展相关概念。

我们将span分为三类：

1.1 EntrySpan
EntrySpan代表一个服务的提供方，即，服务端的入口点。它是每个Java对外服务的入口点。如：Web服务入口就是一个EntrySpan。

1.2 LocalSpan
LocalSpan代表一个普通的Span,代表任意一个本地逻辑块（或方法）

1.3 ExitSpan
ExitSpan也可以称为LeafSpan(SkyWalking的早期版本中的称呼)，代表了一个远程服务的客户端调用。如：一次JDBC调用。

### 二. ContextCarrier
分布式追踪要解决的一个重要问题，就是跨进程调用链连接的问题，ContextCarrier的概念就是为了解决这种场景。

当发生一次**A->B**的网络调用时：
1. 创建一个空的ContextCarrier
1. 通过`ContextManager#createExitSpan`方法创建一个ExitSpan，或者使用`ContextManager#inject`，在过程中传入并初始化`ContextCarrier`
1. 将`ContextCarrier`中所有元素放入请求头（如：HTTP头）或消息正文（如 Kafka）
1. `ContextCarrier`随请求传输到服务端
1. 服务端收到后，转换为新的ContextCarrier
1. 通过`ContestManager#createEntrySpan`方法创建EntrySpan，或者使用`ContextManager#extract`，建立分布式调用关联


以HTTPComponent调用Tomcat为例：
1. 客户端（HTTPComponent端）
```java
            span = ContextManager.createExitSpan("/span/operation/name", contextCarrier, "ip:port");
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                httpRequest.setHeader(next.getHeadKey(), next.getHeadValue());
            }
```

2. 服务端（Tomcat端）
```java
            ContextCarrier contextCarrier = new ContextCarrier();
            CarrierItem next = contextCarrier.items();
            while (next.hasNext()) {
                next = next.next();
                next.setHeadValue(request.getHeader(next.getHeadKey()));
            }

            span = ContextManager.createEntrySpan(“/span/operation/name”, contextCarrier);
```

### 三. ContextSnapshot
除了跨进程的RPC调用，另外一种追踪的常见场景是跨线程保持链路连接。跨线程和跨进程有很高的相似度，都是需要完成上下文的传递工作。
所以ContextSnapshot具有和ContextCarrier十分类似的API风格。

当发生一次**A->B**的跨线程调用时：
1. 需要在A线程中通过ContextManager#capture操作生成ContextSnapshot对象实例
1. 将这个ContextSnapshot对象传递到B线程中
1. B线程通过ContextManager#continued操作完成上下文传递

## 核心API
### 一. ContextManager
ContextManager提供了追踪相关操作的主入口

1. 创建EntrySpan
```java
public static AbstractSpan createEntrySpan(String operationName, ContextCarrier carrier)
```
通过服务名、跨进程传递的ContextCarrier，创建EntrySpan。

2. 创建LocalSpan
```java
public static AbstractSpan createLocalSpan(String operationName)
```
根据服务名（或方法名），创建LocalSpan

3. 创建ExitSpan
```java
public static AbstractSpan createExitSpan(String operationName, ContextCarrier carrier, String remotePeer)
```
根据服务名，跨进程传递的ContextCarrier（空容器）和远端服务地址（IP、主机名、域名 + 端口），创建ExitSpan

### 二. AbstractSpan
AbstractSpan提供了Span内部，进行操作的各项API

```java
    /**
     * Set the component id, which defines in {@link ComponentsDefine}
     *
     * @param component
     * @return the span for chaining.
     */
    AbstractSpan setComponent(Component component);

    /**
     * Only use this method in explicit instrumentation, like opentracing-skywalking-bridge.
     * It it higher recommend don't use this for performance consideration.
     *
     * @param componentName
     * @return the span for chaining.
     */
    AbstractSpan setComponent(String componentName);

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
    AbstractSpan setOperationName(String operationName);
```
Span的操作语义和OpenTracing类似。

SpanLayer为我们的特有概念，如果是远程调用类的服务，请设置此属性，包括5个属性值
1. UNKNOWN, 默认
1. DB
1. RPC_FRAMEWORK，非HTTP类型的RPC框架，如：原生的DUBBO，MOTAN
1. HTTP
1. MQ

Component ID被SkyWalking项目组定义和保护。0到10000为保留值，如果你希望贡献新插件，可以在插件pull request通过，并提交的自动化
测试用户被接收后，申请自己的组件ID。私有插件，请使用10000以上的ID，避免重复。

## 开发插件
### 一. 简介
因为所有的程序调用都是基于方法的，所以插件实际上就是基于方法的拦截，类似面向切面编程的AOP技术。SkyWalking底层已经完成相关的技术封装，所以插件开发者只需要定位需要拦截的类、方法，然后结合上文中的追踪API，即可完成插件的开发。

### 二. 拦截类型
根据Java方法，共有三种拦截类型
1. 拦截构造函数
1. 拦截实例方法
1. 拦截静态方法

我们将这三类拦截，分为两类，即：
1. 实例方法增强插件，继承ClassInstanceMethodsEnhancePluginDefine
1. 静态方法增强插件，继承ClassStaticMethodsEnhancePluginDefine

当然，也可以同时支持实例和静态方法，直接继承ClassEnhancePluginDefine。但是，这种情况很少。

### 三. 实现自己的插件定义
我们以继承ClassInstanceMethodsEnhancePluginDefine为例（ClassStaticMethodsEnhancePluginDefine十分类似，不再重复描述），描述定义插件的全过程

1. 定义目标类名称
```java
protected abstract ClassMatch enhanceClass();
```

ClassMatch反应类的匹配方式，目前提供四种：

* byName, 通过类名完整匹配
* byClassAnnotationMatch, 通过类标注进行匹配
* byMethodAnnotationMatch, 通过方法的标注来匹配类
* byHierarchyMatch, 通过父类或者接口匹配

注意实现：
* 所有类、接口、标注名称，请使用字符串，不要使用`*.class.getName()`(用户环境可能会引起ClassLoader问题)。
* by*AnnotationMatch不支持继承的标注
* byHierarchyMatch，如果存在接口、抽象类、类间的多层继承关系，如果方法复写，则可能造成多层埋点。

如：
```java
@Override
protected ClassMatch enhanceClassName() {
    return byName("org.apache.catalina.core.StandardEngineValve");		
}		      

```

2. 定义方法拦截点
```java
protected InstanceMethodsInterceptPoint[] getInstanceMethodsInterceptPoints();

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

返回拦截方法的匹配器，以及对应的拦截类，同样由于潜在的ClassLoader问题，不要使用`*.class.getName()`。如何构建拦截器，请章节"四. 实现拦截器逻辑"。

3. 定义skywalking-plugin.def文件
```properties
tomcat-7.x/8.x=TomcatInstrumentation
```

* 插件名称，要求全局唯一，命名规范：目标组件+版本号
* 插件定义类全名

### 四. 实现拦截器逻辑
我们继续以实现实例方法拦截为例，拦截器需要实现org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor。
```java
/**
 * A interceptor, which intercept method's invocation. The target methods will be defined in {@link
 * ClassEnhancePluginDefine}'s subclass, most likely in {@link ClassInstanceMethodsEnhancePluginDefine}
 *
 * @author wusheng
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

可以在方法执行前、执行后、执行异常三个点，进行拦截，设置修改方法参数（执行前），并调用核心API，设置追踪逻辑。

## 贡献插件到主仓库
我们鼓励大家共同贡献支持各个类库的插件。

大家需支持以下步骤执行：
1. 在issue页面提出插件扩展需求，对应的版本。
1. Fork apache/incubator-skywalking到本地
1. 在apm-sniffer/apm-sdk-plugin下新建自己的插件模块，模块名为：支持类库名称+版本号
1. 按照规范开发插件
1. 完善注释和测试用例
1. 在本地打包进行集成测试
1. 提交Pull Request到 apache/incubator-skywalking，根据评审团队要求，提供相关自动化测试用例
1. SkyWalking Committer成员完成插件审核，确定发布版本，并合并到主仓库。
