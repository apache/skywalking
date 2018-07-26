# 架构设计
## 背景
对于APM来说，自动探针和手动探针，只是关于如何实现监控的技术细节。这些和架构设计无关。因此在本文档中，我们将它们仅视为客户端库。

<img src="https://skywalkingtest.github.io/page-resources/5.0/architecture.png"/>

## 基本原则
SkyWalking架构的基本设计原则包括**易于维护、可控和流式处理**。

为了实现这些目标，SkyWalking后端采用以下设计。
1. 模块化设计。
2. 多种客户端连接方式。
3. 后端集群服务发现机制。
4. 流模式。
5. 可切换的存储模块。

## 模块化
SkyWalking后端基于纯**模块化设计**。用户可以根据自己的需求切换或组装后端功能。

### 模块

模块定义了一组特性，这些特性可以包括技术库(如:gRPC/Jetty服务器管理)、跟踪分析(如:跟踪段或zipkin span解析器)或聚合特性。
这些完全由模块定义及其模块实现来决定。

每个模块都可以在Java接口中定义它们的服务，每个模块的提供者都必须为这些服务提供实现者。
提供者应该基于自己的实现定义依赖模块。这意味着，即使两个不同的模块实现者，也可以依赖不同的模块。

此外，后端模块化core还检查启动序列，如果没有发现周期依赖或依赖，后端应该被core终止。

后端启动所有模块，这些模块的配置在`application.yml`中是分离的。在这个yaml文件中：
- 根级别是模块名称，例如`cluster`、`naming`等。
- 第二级别是模块的实现者的名称，例如`zookeeper`是`cluster`等模块。
- 第三级是实现者的具体属性。例如`hostPort`和`sessionTimeout`是`zookepper`的必需属性。

_yaml文件的一部分举例_
```yml
cluster:
  zookeeper:
    hostPort: localhost:2181
    sessionTimeout: 100000
naming:
  jetty:
    #OS real network IP(binding required), for agent to find collector cluster
    host: localhost
    port: 10800
    contextPath: /
```

## 多种连接方式
首先，后端提供两种类型的连接，也即提供两种协议（HTTP和gRPC）:
1. HTTP中的命名服务，它返回后端群集中的所有可用collector地址。
2. 在gRPC(SkyWalking原生探针的主要部分)和HTTP中使用上行链路服务，它将跟踪和度量数据上传到后端。
每个客户端将只向单个collector发送监视数据(跟踪和度量)。如果与连接的后端在某个时刻断开连接将会尝试连接其它的后端。

比如在SkyWalking Java探针中
1. `collector.servers`表示命名服务，将`naming/jetty/ip:port`映射为HTTP请求地址。
2. `collector.direct_servers` 表示直接设置上行服务，并使用gRPC发送监控数据。


_客户端库和后端集群之间的流程图_
```
         Client lib                         Collector1             Collector2              Collector3
 (Set collector.servers=Collector2)              (Collector 1,2,3 constitute the cluster)
             |
             +-----------> naming service ---------------------------->|
                                                                       |
             |<------- receive gRPC IP:Port(s) of Collector 1,2,3---<--|
             |
             |Select a random gRPC service
             |For example collector 3
             |
             |------------------------->Uplink gRPC service----------------------------------->|
```


## Collector 集群发现
当Collector以群集模式运行时，后端必须以某种方式相互发现。默认情况下，SkyWalking使用Zookeeper进行协调，并作为实例发现的注册中心。

通过以上部分([多个连接方式](#多种连接方式))，客户端库将不会使用Zookeeper来查找集群。我们建议客户不要这么做。因为集群发现机制是可切换的，由模块化核心提供。依赖它会破坏可切换能力。
我们希望社区提供更多的实现者来进行集群发现，例如Eureka，Consul，Kubernate等。


## 流模式
流模式类似轻量级storm/spark的实现，它允许使用API​​构建流处理图（DAG）以及每个节点的输入/输出数据协定。

新模块可以查找和扩展现有的流程图。

处理中有三种情况
1. 同步过程，传统方法调用。
2. 异步过程，又叫做基于队列缓冲区的批处理。
3. 远程过程，汇总后端的汇总。以这种方式，在节点中定义选择器以决定如何在集群中找到collector。（HashCode，Rolling，ForeverFirst是支持的三种方式）

通过具备的这些功能，collector集群像流式网络一样运行着去聚合、度量标准监控信息，并且不依赖于存储模块的实现来支持并发地编写相同的度量id。

## 可切换存储实现器
由于流模式负责并发，因此存储模块的实现的职责是提供高速写入和组合查询。

目前，我们支持ElasticSearch作为主要实现模块，H2用于预览版本，以及由Sharding Shpere项目管理的MySQL关系数据库集群。

# Web 界面
除了后端设计的原则，UI是SkyWalking的另一个核心组件。它基于React，Antd和Zuul代理实现，提供后端集群发现、查询调度和可视化的功能。

Web UI以[多连接方式](#多种连接方式)中的相似的流程机制作为客户端的`1.naming`、`2.uplink`。唯一的区别是，在`ui/jetty/yaml`定义下的主机和端口上(默认值:localhost:12800)用HTTP绑定中的GraphQL查询协议替换上行。

