## 中文文档
[![EN doc](https://img.shields.io/badge/document-English-blue.svg)](README.md)

注：中文文档由社区志愿者提供，官方文档以英文为准。

  * 快速入门
    * [快速入门](cn/Quick-start-CN.md)
    * [部署 javaagent](cn/Deploy-skywalking-agent-CN.md)
    * [集群模式部署](cn/Deploy-backend-in-cluster-mode-CN.md)
    * [中间件,框架与类库支持列表](Supported-list.md)
      * [如何关闭特定插件?](cn/How-to-disable-plugin-CN.md)
      * [可选插件](cn/Optional-plugins-CN.md)
        * [Spring beans 插件](cn/agent-optional-plugins-CN/Spring-bean-plugins-CN.md)
        * [Oracle and Resin 插件](cn/agent-optional-plugins-CN/Oracle-Resin-plugins-CN.md)
        * [[**孵化特性**] 自定义配置忽略追踪信息](../apm-sniffer/optional-plugins/trace-ignore-plugin/README_CN.md)
  * [架构设计](cn/Architecture-CN.md)  
  * 高级特性
    * [通过系统启动参数进行覆盖配置](cn/Setting-override-CN.md)
    * [服务直连(Direct uplink)及禁用名称服务(naming service)](cn/Direct-uplink-CN.md)
    * [开启TLS](cn/TLS-CN.md)
    * [命名空间隔离](cn/Namespace-CN.md)
    * [基于Token认证](cn/Token-auth-CN.md)
    * [添加自定义组件库](cn/Component-libraries-extend-CN.md)
  * 孵化特性
    * [个性化服务过滤](../apm-sniffer/optional-plugins/trace-ignore-plugin/README_CN.md)
    * [使用Shardingjdbc作为存储实现](cn/Use-ShardingJDBC-as-storage-implementor-CN.md)
  * APM相关介绍资料
    * [OpenTracing中文版](https://github.com/opentracing-contrib/opentracing-specification-zh)
  * Application Toolkit，应用程序工具包
    * [概述](cn/Application-toolkit-CN.md)
    * [使用SkyWalking的OpenTracing的兼容API](cn/Opentracing-CN.md)
    * 日志组件集成
      * [log4j组件](cn/Application-toolkit-log4j-1.x-CN.md)
      * [log4j2组件](cn/Application-toolkit-log4j-2.x-CN.md)
      * [logback组件](cn/Application-toolkit-logback-1.x-CN.md)
    * [使用SkyWalking手动追踪API](cn/Application-toolkit-trace-CN.md)
    * [跨线程任务追踪](cn/Application-toolkit-trace-cross-thread-CN.md) 
  * 测试用例
    * [插件测试](https://github.com/SkywalkingTest/agent-integration-test-report)
    * [Java 探针性能测试](https://skywalkingtest.github.io/Agent-Benchmarks/README_zh.html)
  * 开发指南
    * [工程编译指南](cn/How-to-build-CN.md)
    * [插件开发指南](cn/Plugin-Development-Guide-CN.md)
    * 交互协议
        * [Cross Process Propagation Headers Protocol, v1.0  跨进程追踪上下文传递协议](cn/Skywalking-Cross-Process-Propagation-Headers-Protocol-CN-v1.md)
        * [SkyWalking Trace Data Protocol 探针与Collector间网络协议](cn/Trace-Data-Protocol-CN.md)
  * [Roadmap](ROADMAP.md)
  * 社区提供的共享资源
    * [公开演讲](https://github.com/OpenSkywalking/Community#public-speakings)
    * [视频](https://github.com/OpenSkywalking/Community#videos)
    * [文章](https://github.com/OpenSkywalking/Community#articles)
  * FAQ
    * [Trace查询有数据，但是没有拓扑图和JVM数据?](cn/FAQ/Why-have-traces-no-others-CN.md)
    * [加载探针，Console被GRPC日志刷屏](cn/FAQ/Too-many-gRPC-logs-CN.md)
    * [Kafka消息消费端链路断裂](cn/FAQ/Kafka-plugin-CN.md)
    * [Protoc-Plugin Maven编译时异常](cn/FAQ/Protoc-Plugin-Fails-When-Build-CN.md)
    * [EnhanceRequireObjectCache 类转换异常](cn/FAQ/EnhanceRequireObjectCache-Cast-Exception-CN.md)
    * [skywalking导入eclipse依赖项目异常](cn/FAQ/Import-Project-Eclipse-RequireItems-Exception.md)
