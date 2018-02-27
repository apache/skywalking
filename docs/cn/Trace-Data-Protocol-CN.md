# Trace Data Protocol 中文
Trace Data Protocol协议，也就是探针与Collector间通讯协议

## 概述
此协议包含了Agent上行/下行数据的格式，可用于定制开发，或者探针的多语言扩展

### 协议版本
v1.1

### 协议类型
* 服务发现使用http服务
* 注册和数据上行服务同时支持gRPC和HTTP服务

#### gRPC协议定义文件
[gRPC proto files](../../apm-protocol/apm-network/src/main/proto)

## Collector服务发现协议
### 简介
**Collector服务发现协议是探针启动时，第一个调用的服务。** 通过服务，查找可用的gRPC服务地址列表，并在由客户端选择其中任意一个作为服务端。
此服务建议周期性调用，确保探针本地的服务端口列表是准确有效的。

### 协议类型
HTTP GET

### 协议内容
- 请求
GET操作：http://collectorIp:port/agent/grpc 。 其中`/agent/grpc`是默认值，如需修改，需要参考collector相关配置。

- 返回
JSON数组，数组的每个元素，为一个有效的gRPC服务地址。
```json
["ip address1:port1","ip address2:port2","ip address3:port3"]
```

## 应用注册服务
### 简介
应用注册服务，是将applicationCode，以及ip:port构成的服务地址，转换成数字ID的服务。
此服务会在后续的传输过程中，有效降低网络带宽需求。

### 协议内容
[gRPC service define](../..apm-protocol/apm-network/src/main/proto/ApplicationRegisterService.proto)

- 首次调用时，applicationCode为客户端设置的应用名（显示在拓扑图和应用列表上的名字）。之后随着追踪过程，会上报此应用相关的周边服务的`ip:port`地址列表
- KeyWithIntegerValue 返回，key为上报的applicationCode或ip:port地址，value为对应的id。applicationCode对应的返回id,在后续协议中，被称为applicationId。
- 此服务按需调用，本地无法找到ip:port对应的id时，可异步发起调用。
- 获取applicationId的操作是必选。后续追踪数据依赖此id
- 获取ip:port对应的id是可选，使用id，会有效提高collector处理效率，降低网络消耗。

## 应用实例发现服务
### 简介
应用实例发现服务存在三个子服务，分别是实例注册服务，实例心跳服务，实例注册重连服务。这三个服务负责获取和保持应用实例在线的功能。

### 协议类型
gRPC服务

### 实例注册服务
[gRPC service define](../../apm-protocol/apm-network/src/main/proto/DiscoveryService.proto#L11-L12)

- agentUUID 由探针生成，需保持唯一性，推荐使用UUID算法。并在应用重启前保持不变
- applicationId 由**应用注册服务**获取。
- 服务端返回应用实例id，applicationInstanceId 。后续上报服务使用实例id标识。

### 实例心跳服务
[gRPC service define](../../apm-protocol/apm-network/src/main/proto/DiscoveryService.proto#L14-L15)

- 心跳服务每分钟上报一次。
- 如果一分钟内有segment数据上报，则可不必上报心跳。

## 服务名注册发现服务
### 简介
服务名注册发现服务，是将应用内的服务名（operationName）替换为id的服务。

### 协议内容
[gRPC service define](../../apm-protocol/apm-network/src/main/proto/DiscoveryService.proto#L53-L74)

- 可选服务，可有效降低网络消耗，推荐实现。注意，由于部分应用存在URI中夹带参数的情况，请注意限制探针内的缓存容量，防止内存溢出。
- ServiceNameElement中，applicationId为当前applicationCode对应的id。serviceName一般为对应span的operationName

## JVM指标上报服务
### 简介
上报当前实例的JVM信息，每秒上报一次。

### 协议内容
[gRPC service define](../../apm-protocol/apm-network/src/main/proto/JVMMetricsService.proto)

## TraceSegment上报服务
### 简介
上报调用链信息

### 协议内容
[gRPC service define](../../apm-protocol/apm-network/src/main/proto/TraceSegmentService.proto)

- UniqueId为segment或者globalTraceId的数字表示。由3个long组成，1）applicationInstanceId，2）当前线程id，3）当前时间戳*10000 + seq(0-10000自循环)
- Span的数据，请参考[插件开发规范](Plugin-Development-Guide-CN.md)
- 以下id和名称根据注册返回结果，优先上报id，无法获取id时，再上传name。参考之前的应用和服务注册章节。
  - operationNameId/operationName 
  - networkAddress/networkAddressId
  - entryServiceName/entryServiceId
  - parentServiceName/parentServiceId
  - peerId/peer
- componentId为默认支持的插件id，非官方支持，需传输名称或修改服务端源代码。[官方组件列表](../../apm-protocol/apm-network/src/main/java/org/apache/skywalking/apm/network/trace/component/ComponentsDefine.java)
