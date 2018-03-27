# Skywalking Cross Process Propagation Headers Protocol
* Version 1.0

Skywalking是一个偏向APM的分布式追踪系统，所以，为了提供服务端处理性能。头信息会比其他的追踪系统要更复杂一些。
你会发现，这个头信息，更像一个商业APM系统，并且，一些商业APM系统的头信息，比我们的要复杂的多。

# Header Item
* Header Name: `sw3`
* Header Value: 使用`|`分隔，包含以下内容

_消息头使用sw3，因为此协议始于SkyWalking 3.x版本。_

## Values
* Trace Segment Id

Trace segment，即分布式调用链片段。这个ID为此调用链片段全局唯一ID。此ID由一次分布式调用链的一个线程执行过程独享（在Java模型中）。ID由三个Long型组成，如： `"1.2343.234234234`
  1) 第一部分代表应用的实例ID(`application instance id`)，此ID通过注册接口由Collector分配。一般取值范围为整形，利于protobuf传输。
  2) 第二部分为线程号，Java模型中，一般也是整形。
  3) 第三部分又由两部分组成
     1) 时间戳，单位毫秒
     2) 线程内的自增序列。0到9999之间。

如果你使用其他语言实现探针，你只需要保证你的ID由三个Long型构成，并全局唯一，不必完全遵守Java的ID生成规则。


* Span Id

一个整数，在trace segment内唯一，从0开始自增。

* Parent Application Instance

父级应用节点的应用实例ID。如：在一个RPC调用中，HEAD中是客户端的应用实例ID。

* Entry Application Instance

入口应用节点的应用实例ID。如：在一个分布式链路`A->B->C`中，此字段为`A`应用的实例ID。

* Peer Host

服务端的Peer Host或Peer Id。如：客户端使用`182.14.39.1:9080`服务端，则这个就是对应的Peer Host。

_此值可以通过Collector服务获得对应的ID。如果非ID，则使用`#`开头，如果使用ID，则为整数类型。_

* Entry Span Operation Name of First Trace Segment

调用链入口节点的应用实例下,入口Span的operation name或id。

_此值可以通过Collector服务获得对应的ID。如果非ID，则使用`#`开头，如果使用ID，则为整数类型。_

* Entry Span Operation Name of Parent Trace Segment

调用链父级节点的应用实例下,入口Span的operation name或id。

_此值可以通过Collector服务获得对应的ID。如果非ID，则使用`#`开头，如果使用ID，则为整数类型。_

* Distributed Trace Id

分布式链路ID一般是整个调用链的全局唯一ID。如果针对批量消费情况，这个ID是批量中，第一个生产者的trace ID。此ID生成规则和`Trace Segment Id`一致，由三个Long型数字构成。

### Sample value
value值示例：
1. `1.2343.234234234|1|1|1|#127.0.0.1:8080|#/portal/|#/testEntrySpan|1.2343.234234234`
1. `1.2343.234234234|1|1|1|#127.0.0.1:8080|#/portal/|1038|1.2343.234234234`