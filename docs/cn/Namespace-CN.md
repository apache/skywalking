# 命名空间
## 版本支持
5.0.0-beta +

## 需求背景
SkyWalking是一个用于从分布式系统收集指标的监控工具。 在实际环境中，一个非常大的分布式系统包括数百个应用程序，数千个应用程序实例。 在这种情况下，更大可能的不止一个组，
甚至还有一家公司正在维护和监控分布式系统。 他们每个人都负责不同的部分，不能共享某些指标。

在这种情况下,命名空间就应运而生了,它用来隔离追踪和监控数据.

## 配置命名空间
### 在探针配置中配置 agent.namespace 
```properties
# The agent namespace
# agent.namespace=default-namespace
``` 

默认情况下 `agent.namespace` 是没有配置的. 

**影响**
默认情况下,SkyWalking 设置的key是 `sw3`, 更多信息查看[文档](Skywalking-Cross-Process-Propagation-Headers-Protocol-CN-v1.md).
配置好 `agent.namespace` 之后,  key 就被设置为`namespace:sw3`.

当双方使用不同的名称空间时，跨进程传播链会中断。

### collector 中设置命名空间
```yml
configuration:
  default:
    namespace: xxxxx
```

**影响**
1. 如果使用 `zookeeper`开启了集群模式,`zookeeper`的路径会变为带有命名空间前缀的的路径.
1. 如果使用`Elasticsearch` 进行存储,所有的`type` 名称会带有命名空间的前缀.


