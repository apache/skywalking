### 现象
agent 启动日志出现如下错误,无法将`EnhanceRequireObjectCache`转换为`EnhanceRequireObjectCache`,无法正常上报数据
```java
ERROR 2018-05-07 21:31:24 InstMethodsInter :  class[class org.springframework.web.method.HandlerMethod] after method[getBean] intercept failure
java.lang.ClassCastException: org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache cannot be cast to org.apache.skywalking.apm.plugin.spring.mvc.commons.EnhanceRequireObjectCache
	at org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.GetBeanInterceptor.afterMethod(GetBeanInterceptor.java:45)
	at org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstMethodsInter.intercept(InstMethodsInter.java:105)
	at org.springframework.web.method.HandlerMethod.getBean(HandlerMethod.java)
	at org.springframework.web.servlet.handler.AbstractHandlerMethodExceptionResolver.shouldApplyTo(AbstractHandlerMethodExceptionResolver.java:47)
	at org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver.resolveException(AbstractHandlerExceptionResolver.java:131)
	at org.springframework.web.servlet.handler.HandlerExceptionResolverComposite.resolveException(HandlerExceptionResolverComposite.java:76)
	...
```

### 原因
此类错误见于开发环境使用了热部署(`spring-boot-devtool`)或者其他类似的工具, `classloader` 变更导致.
### 解决方法
1. 此错误不会影响生产环境使用[spring-boot-devtools说明](https://docs.spring.io/spring-boot/docs/current/reference/html/using-boot-devtools.html)
2. 开发环境如果想正常调试,可以暂时在开发环境去掉此包进行调试.
