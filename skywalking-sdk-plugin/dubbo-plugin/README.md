# 如何追踪dubbo调用？
- 引入所需插件
```xml
<!-- dubbo插件，监控dubbo/dubbox调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-dubbo-plugin</artifactId>
    <version>{latest_version}</version>
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
    <version>{latest_version}</version>
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
    <version>{latest_version}</version>
</dependency>
```
- 由于引入dubbox，主要目的是使用rest+json协议，所以以下方案都是在此种调用模式下的解决方案。其他协议未测试，请谅解，望大家提供测试结果与反馈。
- dubbox 2.8.3 以及之前版本不能正确的支持RpcContext中的attachment，存在BUG（2.8.4已修复）。采用扩展参数对象的方法支持追踪的传递性。
- 注意：依然推荐升级到dubbox 2.8.4，此时能更好的进行追踪，并对程序侵入性更小。
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
- dubbox调用参数包含javabean参数，并继承com.ai.cloud.skywalking.plugin.dubbox.bugfix.below283.SWBaseBean。只包含java基础类型（如：String、Integer等）的调用，无法支持追踪传递
- 客户端如果直接使用非dubbox客户端发起http restful调用，需要在发送的参数中设置contextData。
