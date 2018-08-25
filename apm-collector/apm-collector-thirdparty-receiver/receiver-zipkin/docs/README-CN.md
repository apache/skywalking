# Zipkin接收器
[Zipkin](http://zipkin.io/)接收器提供了接受Zipkin格式的span数据功能。SkyWalking可以分析、聚合并且将数据展示到可视化界面。因此,用户不需要了解SkyWalking的自动采集探针(Java, .NET, node.js)是如何工作的，或者这些用户因为某些原因不想改变已有的采集方式，例如在Zipkin的集成工作已经完成的情况下。

Zipkin接受器在SkyWalking中仅仅只是一个可选特性，即使到目前为止它依然是[孵化特性](../../../../docs/cn/Incubating/Abstract-CN.md)。

## 局限性
作为孵化功能，它仍是一个原型。所以它包含以下几点局限：

1. 不要在同一分布式系统中使用SkyWalking原生探针和Zipkin的类库。考虑到Zipkin和SkyWalking的HEADER不是共享/可互操作的，它们两者之间不会相互传播context。这会导致已跟踪的链路中断。
2. 不支持集群模式。
3. 在进行链路分析时，由于链路会在指定时间内结束。默认链路执行时间不超过2分钟。SkyWalking使用了更复杂的header和context结构，来避免zipkin分析时的这个问题。

## 开启Zipkin接收器
Zipkin接收器是一个可选模块，默认是关闭的。在collector的application.yml配置文件中修改下面的配置项来开启此功能：
```yaml
receiver_zipkin:
  default:
    host: localhost
    port: 9411
    contextPath: /
    expireTime: 20  # 单位为秒
    maxCacheSize: 1000000  # traces buffer的大小
```
