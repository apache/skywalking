# 如何追踪注册在Spring中类实例的方法调用？
- 引入所需插件
```xml
<!-- Spring插件，监控所有Spring托管对象的调用-->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-spring-plugin</artifactId>
    <version>1.0-RELEASE</version>
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
- 对于方法的追踪，仅限于实例级的public方法。其他方法由于Java运行时原因，无法追踪。
- 部分类由于被Spring代理后，类名发生变化，可能造成无法追踪

