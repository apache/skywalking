## 服务网格探针
服务网格探测器通过服务网格中提供的可扩展机制来实现, 如istio。

## 什么是服务网格？
下面引用了Istio的官方文档。
> The term service mesh is often used to describe the network of microservices that make up such applications and the interactions between them. 
As a service mesh grows in size and complexity, it can become harder to understand and manage. 
Its requirements can include discovery, load balancing, failure recovery, metrics, and monitoring, and often more complex operational requirements 
such as A/B testing, canary releases, rate limiting, access control, and end-to-end authentication.

## 探测器从哪里收集数据？
Istio是一个很典型的服务网格的设计与实现。其中定义了被广泛使用的**控制面板**和**数据面板**。Istio架构图如下：

<img src="https://istio.io/docs/concepts/what-is-istio/img/overview/arch.svg"/>

服务网格探测器可以选择从**控制面板**或**数据面板**收集数据。在Istio中，意味着将从Mixer（控制面板）或者Envoy sidecar（数据面板）收集数据。
它们的基础数据是相同的, 探测器收集在每个请求中从客户端和服务器端收集遥测实体。

## 服务网格如何使后端工作？
从这种探测器中国呢可以看到并没有追踪相关的数据，那么SkyWalking为何依然能够正常工作呢？

服务网格探测器从每次请求中收集遥测数据，因此探测器知道请求的来源、目的、端点、延时和状态。
通过这些信息，SkyWalking后端通过结合这些和经过每个节点的来源数据的指标信息得到整体的拓扑图。
后端在解析追踪数据的时候需要保持一致的指标信息。因此，正确阐述则是：
**服务网格指标是追踪解析器生成的指标。他们是一样的。**

## 下一步是什么？
- 如果你想使用服务网格探测器, 阅读[在Service Mesh中设置SkyWalking](../setup/README.md#on-service-mesh) document.
