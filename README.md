Sky Walking
==========

<img src="http://wu-sheng.github.io/sky-walking/images/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

SkyWalking: Large-Scale Distributed Systems Tracing Infrastructure, 是一个对JAVA分布式应用程序集群的业务运行情况进行追踪、告警和分析的系统。

[![Build Status](https://travis-ci.org/wu-sheng/sky-walking.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking)

* 核心理论为[Google Dapper论文：Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html),英语有困难的同学可参考[国内翻译](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* 本分析系统能通过不修改或少量修改代码的模式，对现有的JAVA应用或J2EE应用进行监控和数据收集，并针对应用进场进行准实时告警。此外提供大量的调用性能分析功能，解决目前的监控系统主要监控进程、端口而非应用实际性能的问题。
* 支持国内常用的dubbo以及dubbox等常见RPC框架，支持应用异常的邮件告警
* skywalking-sdk层面提供的埋点API，同步阻塞访问时间小于100μs
* 通过[byte-buddy](https://github.com/raphw/byte-buddy)，部分插件将通过动态字节码机制，避免代码侵入性，完成监控。动态代码模式埋点，同步阻塞访问时间应在200-300μs
* SkyWalking is an open source Large-Scale Distributed Systems Tracing Infrastructure, also been known as APM(Application Performance Management) tool. SkyWalking provides a solution to help monitor and analysis a Large-Scale Distributed Systems.
* SkyWalking supports popular rpc frameworks, such as [dubbo](https://github.com/alibaba/dubbo), [dubbox](https://github.com/dangdangdotcom/dubbox), etc., supports email-alert when application occurs unexpected exception。
* SkyWalking's basic API, execution time of blocking saving span must less than 100μs.
* By using [byte-buddy](https://github.com/raphw/byte-buddy) (Thanks to [raphw](https://github.com/raphw)), some plugins use dynamic byte code generation to avoid invasive codes. plugins API, execution time of blocking saving span must between 200μs and 300μs, including execution time dynamic byte code.

|plugins|using config file|using dynamic byte code| coding |remarks|
| ----------- |---------| ----------|----------|----------|
|web-plugin|web.xml| - | - | - |
|dubbo-plugin| dubbo/dubbox config file | - | - | - |
|spring-plugin| spring config file | - | - | - |
|jdbc-plugin| jdbc config file | - | - | - |
|mysql-plugin| - | YES | - | - |
|httpClient-4.x-plugin| - | YES | - | - |
|httpClient-4.x-plugin-dubbox-rest-attachment| - | YES | - | required client-4.x-plugin |
|jedis-2.x-plugin| - | YES | - | - |
|~~httpclient-4.2.x-plugin~~| - | - | YES | 需要使用新提供的httpClient包装对象 |
|~~httpclient-4.3.x-plugin~~| - | - | YES | 需要使用新提供的httpClient包装对象 |

* 删除插件为最新版本不推荐使用的插件

# 新版本能力规划
* 提供一定的日志数据分析和展现能力，减少或者避免使用团队的二次开发

# 主要贡献者
* 吴晟 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) wusheng@asiainfo.com
* 张鑫 &nbsp;&nbsp;[亚信](http://www.asiainfo.com/) zhangxin10@asiainfo.com

# 交流
* 联系邮箱：wu.sheng@foxmail.com
* QQ群：392443393，请注明“Sky Walking交流”
* 谁在使用Sky Walking?[点击进入](https://github.com/wu-sheng/sky-walking/issues/34)。同时请各位使用者反馈下，都在哪些项目中使用。
* if you are using SkyWalking，[Report to us](https://github.com/wu-sheng/sky-walking/issues/34) please.

# 整体架构图
![整体架构图](http://wu-sheng.github.io/sky-walking/sample-code/images/skywalkingClusterDeploy.jpeg)

# 典型页面展现 / Typical UI show
## 实时调用链路
* 实时链路追踪展现
![追踪连路图1](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/callChain.png)
* 实时链路追踪详细信息查看
![追踪连路图2](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/callChainDetail.png)
* 实时链路追踪日志查看
![追踪连路图3](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/callChainLog.png)
* 实时链路异常告警邮件
![告警邮件](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/alarmMail.jpg)

## 分析汇总

# Quick Start
## 编译与部署 / Build and deploy
- 参考《[代码编译部署说明](BUILD_DOC.md)》

## 引入核心SDK / Import SDK
- 无论试用哪种插件，都必须引入
- add dependencies to pom.xml
```xml
<!-- API日志输出，客户端可指定所需的log4j2版本 -->
<!-- 2.4.1为开发过程所选用版本 -->
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.4.1</version>
</dependency>
<!-- 监控api，可监控插件不支持的调用 -->
<dependency>
    <groupId>com.ai.cloud</groupId>
    <artifactId>skywalking-api</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## 使用全新的main class。原main class，以及参数作为参数传入
- using new main class, instead of the original main class.
```shell
#原进程启动命令：
#original starup command
java com.company.product.Startup arg0 arg1

#全新的进程启动命令：
#new starup command
java com.ai.cloud.skywalking.plugin.TracingBootstrap com.company.product.Startup arg0 arg1
```

## 根据所需插件，配置应用程序 / Config application
- 参考《[SDK Guides](skywalking-sdk-plugin)》
- 注意：插件不会引用所需的第三方组件（如Spring、dubbo、dubbox等），请自行引入所需的版本。


## 下载并设置授权文件 / Download auth file
- 注册并登陆过skywalking-webui，创建应用。（一个用户代表一个逻辑集群，一个应用代表一个服务集群。如前后端应用应该设置两个应用，但归属一个用户）
- Sign up and login in skywalking-webui. Create application as needed.
- 下载授权文件，并在运行时环境中，将授权文件加入到CLASSPATH或LIB中
- download auth file(*.jar), and add the jar file to the CLASSPATH or lib.

## 在运行时环境中设置环境变量 / set environment variables 
```
export SKYWALKING_RUN=true
```
- 设置完成后，可以在当前环境中启动业务应用系统
- [在应用程序中显示traceid](HOW_TO_FIND_TID.md)

# 源代码说明
* [追踪日志明细存储结构说明](https://github.com/wu-sheng/sky-walking/blob/master/skywalking-server/doc/hbase_table_desc.md)
* [the storage structure of tracking logs](https://github.com/wu-sheng/sky-walking/blob/master/skywalking-server/doc/hbase_table_desc.md)
