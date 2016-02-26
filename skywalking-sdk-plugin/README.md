# 如何追踪注册在Spring中类实例的方法调用？
- Spring配置文件头部，配置所需的命名空间
```xml
xmlns:skywalking="http://cloud.asiainfo.com/schema/skywalking"
xsi:schemaLocation="http://cloud.asiainfo.com/schema/skywalking
		   http://cloud.asiainfo.com/schema/skywalking/skywalking.xsd"
```
- 典型Spring配置文件如下
```xml
<beans xmlns="http://www.springframework.org/schema/beans"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
	xmlns:context="http://www.springframework.org/schema/context"
	xmlns:skywalking="http://cloud.asiainfo.com/schema/skywalking"
	xsi:schemaLocation="http://www.springframework.org/schema/beans
				http://www.springframework.org/schema/beans/spring-beans-3.0.xsd
				http://www.springframework.org/schema/context
				http://www.springframework.org/schema/context/spring-context-3.0.xsd
				http://cloud.asiainfo.com/schema/skywalking
				http://cloud.asiainfo.com/schema/skywalking/skywalking.xsd">
```
- Spring配置文件中，设置需要追踪包名、类名或方法名。可配置多个
```xml
<skywalking:trace packageExpression="com.ai.app.domain.test.*" classExpression="*"/>
<skywalking:trace packageExpression="com.ai.app.domain.test..*" classExpression="className*"/>
```
- 对于方法的追踪，仅限于实例级的public方法。其他方法由于Java运行时原因，无法追踪。
- 部分类由于被Spring代理后，类名发生变化，可能造成无法追踪

# 如何追踪dubbo调用？
- 这里的dubbo，专指阿里发布的，已停止维护的标准dubbo版本（[dubbo.io](http://dubbo.io/)）。扩展版本dubbox请参考相关章节。
- 在客户端和服务端配置全局filter：swEnhanceFilter。
```xml
//客户端Spring配置文件
<dubbo:consumer filter="swEnhanceFilter"/>

//服务端Spring配置文件
<dubbo:provider filter="swEnhanceFilter"/>
```

# 如何追踪dubbox 2.8.4 调用？ 
- dubbox 2.8.4 较为符合dubbo的服务规范，这里指dubbox支持RpcContext中的attachment。追踪方式和dubbo相同。
- 在客户端和服务端配置全局filter：swEnhanceFilter。
```xml
//客户端Spring配置文件
<dubbo:consumer filter="swEnhanceFilter"/>

//服务端Spring配置文件
<dubbo:provider filter="swEnhanceFilter"/>
```

# 如何追踪dubbox 2.8.3 以及之前的调用？ 
- 由于引入dubbox，主要目的是使用rest+json协议，所以以下方案都是在此种调用模式下的解决方案。其他协议未测试，请谅解，望大家提供测试结果与反馈。
- dubbox 2.8.3 以及之前版本不能正确的支持RpcContext中的attachment，存在BUG（2.8.4已修复）。采用扩展参数对象的方法支持追踪的传递性。
- 注意：依然推荐升级到dubbox 2.8.4，此时能更好的进行追踪，并对程序侵入性更小。