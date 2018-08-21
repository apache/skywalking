# 服务直连(Direct uplink)
## 版本支持
5.0.0-beta +

## 什么是服务直连(Direct uplink)?
默认情况下, `SkyWalking`探针使用 名称服务(naming service,即通过名称获取服务地址)的形式获取 `collector`的地址连接gRPC服务.

 **服务直连** 意味着在名称服务不可用或者低可用的情况下,在探针端直接使用设置的gRPC的地址进行连接.

## 为什么需要这样做?
如果探针通过以下代理上报数据:
1. 私有云(VPCs)
1. 公网(Internet)
1. 不同的子网(subnet).
1. Ip,Port代理

## 探针配置
1. 去掉配置 `collector.servers` .
2. 在 `agent.config`中按照如下配置
```
# Collector agent_gRPC/grpc 地址.
# 当不配置"collector.servers"的时候,选择第二种配置地址生效.
# 如果使用此配置,自动发现服务将无法使用,探针将直接使用此地址进行数据上报.
# 仅仅当探针端无法连接到`collector`的集群 ip地址时,我们才推荐使用这种配置,比如:
#   1. 探针和 `collector`在不同的私有云当中.
#   2. 探针通过外网上报数据到 `collector`.
# collector.direct_servers=www.skywalking.service.io
```  

3. 可以只用域名或者IP:PORT形式(逗号分割) 来设置`collector.direct_servers`.

