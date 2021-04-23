### Problem
When you start your application with the `skywalking` agent, you may find this exception in your agent log which means that `EnhanceRequireObjectCache` cannot be casted to `EnhanceRequireObjectCache`. For example:
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

### Reason
This exception may be caused by `hot deployment` tools (`spring-boot-devtool`) or otherwise, which changes the  `classloader` in runtime.

### Resolution
1. This error does not occur under the production environment, since developer tools are automatically disabled: See [spring-boot-devtools](https://docs.spring.io/spring-boot/docs/current/reference/html/using-boot-devtools.html).
2. If you would like to debug in your development environment as usual, you should temporarily remove such `hot deployment` package in your lib path.
