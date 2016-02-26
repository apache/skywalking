# 如何追踪注册在Spring中类实例的方法调用？
- 引入所需插件
```xml
<!-- Spring插件，监控所有Spring托管对象的调用-->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-spring-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
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
- <font color=red>对于方法的追踪，仅限于实例级的public方法。其他方法由于Java运行时原因，无法追踪。</font>
- <font color=red>部分类由于被Spring代理后，类名发生变化，可能造成无法追踪</font>

# 如何追踪dubbo调用？
- 引入所需插件
```xml
<!-- dubbo插件，监控dubbo/dubbox调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-dubbo-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 这里的dubbo，专指阿里发布的，已停止维护的标准dubbo版本（[dubbo.io](http://dubbo.io/)）。扩展版本dubbox请参考相关章节。
- 在客户端和服务端配置全局filter：swEnhanceFilter。
```xml
//客户端Spring配置文件
<dubbo:consumer filter="swEnhanceFilter"/>

//服务端Spring配置文件
<dubbo:provider filter="swEnhanceFilter"/>
```

# 如何追踪dubbox 2.8.4 调用？ 
- 引入所需插件
```xml
<!-- dubbo插件，监控dubbo/dubbox调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-dubbo-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- dubbox 2.8.4 较为符合dubbo的服务规范，这里指dubbox支持RpcContext中的attachment。追踪方式和dubbo相同。
- 在客户端和服务端配置全局filter：swEnhanceFilter。
```xml
//客户端Spring配置文件
<dubbo:consumer filter="swEnhanceFilter"/>

//服务端Spring配置文件
<dubbo:provider filter="swEnhanceFilter"/>
```

# 如何追踪dubbox 2.8.3 以及之前版本的调用？ 
- 引入所需插件
```xml
<!-- dubbo插件，监控dubbo/dubbox调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-dubbo-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 由于引入dubbox，主要目的是使用rest+json协议，所以以下方案都是在此种调用模式下的解决方案。其他协议未测试，请谅解，望大家提供测试结果与反馈。
- dubbox 2.8.3 以及之前版本不能正确的支持RpcContext中的attachment，存在BUG（2.8.4已修复）。采用扩展参数对象的方法支持追踪的传递性。
- <font color=red>注意：依然推荐升级到dubbox 2.8.4，此时能更好的进行追踪，并对程序侵入性更小。</font>
- 在客户端和服务端配置全局filter：swEnhanceFilter。
```xml
//客户端Spring配置文件
<dubbo:consumer filter="swEnhanceFilter"/>

//服务端Spring配置文件
<dubbo:provider filter="swEnhanceFilter"/>
```
- 在客户端和服务端启动时，开启dubbox 2.8.3之前版本的修复功能。在服务启动前调用如下代码，或将com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve注册到Spring中。
```java
new com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.BugFixAcitve();
```
- dubbox调用参数包含javabean参数，并继承com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.SWBaseBean。<font color=red>只包含java基础类型（如：String、Integer等）的调用，无法支持追踪传递</font>
- 客户端如果直接使用非dubbox客户端发起http restful调用，需要在发送的参数中设置contextData。

# 如何追踪MySQL访问？
- 引入所需插件
```xml
<!-- jdbc插件，监控所有的jdbc调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-jdbc-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 设置特定的JDBC Driver
```properties
Driver="com.ai.cloud.skywalking.plugin.jdbc.mysql.MySQLTracingDriver"
```
- 设置特定的JDBC URL
```properties
jdbc.url=tracing:jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=UTF-8
```

# 如何追踪MySQL之外的其他JDBC？ 
- 引入所需插件
```xml
<!-- jdbc插件，监控所有的jdbc调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-jdbc-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 轻松实现自定义的JDBC Driver扩展
```java
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

import com.ai.cloud.skywalking.plugin.jdbc.TracingDriver;

public class XXXDBTracingDriver extends TracingDriver {
	static {
		try {
			DriverManager.registerDriver(new XXXDBTracingDriver());
		} catch (SQLException e) {
			throw new RuntimeException("register "
					+ MySQLTracingDriver.class.getName() + " driver failure.");
		}
	}

	/**
	 * 继承自TracingDriver，返回真实的Driver
	 */
	@Override
	protected Driver registerTracingDriver() {
		try {
			//示例：return new com.mysql.jdbc.Driver();
			return new Driver();
		} catch (SQLException e) {
			throw new RuntimeException("create Driver failure.");
		}
	}
}
```
- 设置新实现的JDBC Driver
```properties
Driver="XXXDBTracingDriver"
```
- 设置特定的JDBC URL
```properties
jdbc.url=tracing:jdbc:xxxdb://localhost:3306/test
```

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
    <filter-namer>tracingFilter</filter-namer>
    <filter-classr>com.ai.cloud.skywalking.plugin.web.SkyWalkingFilter</filter-class>
    <init-param>
        <param-name>tracing-name</param-name>
        <!--分布式埋点信息，默认放在request的header中，key=SkyWalking-TRACING-NAME,可根据需要修改-->
        <param-value>SkyWalking-TRACING-NAME</param-value>
    </init-param>
</filterr>
<filter-mappingr>
    <filter-name>tracingFilter</filter-namer>
    <!--追踪路径应为MVC的请求路径，不建议包括js/css/图片等资源路径-->
    <url-patternr>/request-uri</url-patternr>
</filter-mappingr>
```

# 如何追踪HttpClient发起HTTP调用？

## httpclient 4.2.x
- 引入所需插件
```xml
<!-- httpClient插件，监控httpClient 4.2的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-httpClient-4.2.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```
- 使用SWTracingHttpClient封装所需的httpClient，此httpClient所有调用都会被监控
```java
HttpClient httpclient = new SWTracingHttpClient(new DefaultHttpClient());
```
- 上下文将被存储在http request head，中，默认名称和SkyWalkingFilter保持一致。
- 如果服务端使用dubbox 2.8.4 的提供的http-rest，请使用方法重载
```java
HttpClient httpclient = new SWTracingHttpClient(new DefaultHttpClient(), "Dubbo-Attachments");
```

## httpclient 4.3.x
- 引入所需插件
```xml
<!-- httpClient插件，监控httpClient 4.3的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-httpClient-4.3.x-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
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