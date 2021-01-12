### Problem
when you start your application with `skywalking` agent,if you find this exception in your agent log which mean `EnhanceRequireObjectCache` can not be casted to `EnhanceRequireObjectCache`.eg:
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
this exception may caused by some `hot deployment` tools(`spring-boot-devtool`) or some else which may change the  `classloader` in runtime.
### Resolve 
1. Production environment does not appear this error because developer tools are automatically disabled,look [spring-boot-devtools](https://docs.spring.io/spring-boot/docs/current/reference/html/using-boot-devtools.html)
2. If you want to debug in your development environment normally,you should remove such `hot deployment` package in your lib path temporarily.
