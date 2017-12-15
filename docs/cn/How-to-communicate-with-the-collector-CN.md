# 探针与Collector间通讯协议
## 前言
这篇文章主要介绍3.2版本的Collector对外提供的服务协议。一般情况下，使用者和开发者都无需了解此协议细节。但是在庞大的开源生态中，我们已经收到过多次有公司或个人的使用案例，使用自己的非Java探针（PHP,GO等）探针，接入我们的Collector进行数据分析和监控。

## 协议类型
Collector从3.2开始，对外同时提供gRPC和HTTP RESTFul两种类型的协议。从效率上，我们推荐使用gRPC

# gRPC服务
本章节，描述官方java探针使用的网络协议

## Collector服务发现协议
### 简介
**Collector服务发现协议是探针启动时，第一个调用的服务。**通过服务，查找对应的gRPC服务地址与端口列表，并在由客户端选择其中任意一个作为服务端。此服务需周期性调用，确保探针本地的服务端口列表是准确有效的。

### 协议类型
HTTP GET

### 协议内容
- 请求
GET操作：http://collectorIp:port/agentstream/grpc 。 其中`/agentstream/grpc`是默认值，如需修改，需要参考collector相关配置。

- 返回
JSON数组，数组的每个元素，为一个有效的gRPC服务地址。
```json
["ip address1:port1","ip address2:port2","ip address3:port3"]
```

## 应用注册服务
### 简介
应用注册服务，是将手动设计的applicationCode，以及ip:port沟通的服务地址，转换成数字的服务。此服务会在后续的传输过程中，有效降低网络带宽需求。

### 协议类型
gRPC服务

### 协议内容
https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/proto/ApplicationRegisterService.proto
```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.proto";

import "KeyWithIntegerValue.proto";

//register service for ApplicationCode, this service is called when service starts.
service ApplicationRegisterService {
    rpc register (Application) returns (ApplicationMapping) {
    }
}

message Application {
    repeated string applicationCode = 1;
}

message ApplicationMapping {
    repeated KeyWithIntegerValue application = 1;
}
```
- 首次调用时，applicationCode为客户端设置的应用名（显示在拓扑图和应用列表上的名字）。之后随着追踪过程，会上报此应用相关的周边服务的`ip:port`地址列表
- KeyWithIntegerValue 返回，key为上报的applicationCode和ip:port地址，value为对应的id。applicationCode对应的返回id,在后续协议中，被称为applicationId。
- 此服务按需调用，本地无法找到ip:port对应的id时，可异步发起调用。
- 获取applicationId的操作是必选。
- 获取ip:port对应的id是可选，但是完成id设置，会有效提高collector处理效率，降低网络消耗。


## 应用实例发现服务
### 简介
应用实例发现服务存在三个子服务，分别是实例注册服务，实例心跳服务，实例注册重连服务。这三个服务负责获取和保持应用实例在线的功能。

### 协议类型
gRPC服务

### 实例注册服务
https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/proto/DiscoveryService.proto#L11-L12
```proto
service InstanceDiscoveryService {
    rpc register (ApplicationInstance) returns (ApplicationInstanceMapping) {
    }
}

message ApplicationInstance {
    int32 applicationId = 1;
    string agentUUID = 2;
    int64 registerTime = 3;
    OSInfo osinfo = 4;
}

message OSInfo {
    string osName = 1;
    string hostname = 2;
    int32 processNo = 3;
    repeated string ipv4s = 4;
}

message ApplicationInstanceMapping {
    int32 applicationId = 1;
    int32 applicationInstanceId = 2;
}
```
- agentUUID 由探针生成，需保持唯一性，推荐使用UUID算法。并在应用重启前保持不变
- applicationId 由**应用注册服务**获取。
- 服务端返回应用实例id，applicationInstanceId 。后续上报服务使用实例id标识。

### 实例心跳服务
https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/proto/DiscoveryService.proto#L14-L15
```proto
service InstanceDiscoveryService {
    rpc heartbeat (ApplicationInstanceHeartbeat) returns (Downstream) {
    }
}

message ApplicationInstanceHeartbeat {
    int32 applicationInstanceId = 1;
    int64 heartbeatTime = 2;
}
```
- 心跳服务每分钟上报一次。
- 如一分钟内有segment数据上报，则可不必上报心跳。

### 实例注册重连服务
https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/proto/DiscoveryService.proto#L17-L18
```proto
service InstanceDiscoveryService {
    rpc registerRecover (ApplicationInstanceRecover) returns (Downstream) {
    }
}

message ApplicationInstanceRecover {
    int32 applicationId = 1;
    int32 applicationInstanceId = 2;
    int64 registerTime = 3;
    OSInfo osinfo = 4;
}
```
- 应用重连服务于**应用注册服务**类似，在gRPC发生重连，并再次连接成功后发送。需包含通过**应用注册服务**获取的applicationInstanceId。

## 服务名注册发现服务
### 简介
服务名注册发现服务，是将应用内的服务名（operationName）替换为id的服务。

### 协议类型
gRPC服务

### 协议内容
https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/proto/DiscoveryService.proto#L53-L74
```proto
//discovery service for ServiceName by Network address or application code
service ServiceNameDiscoveryService {
    rpc discovery (ServiceNameCollection) returns (ServiceNameMappingCollection) {
    }
}

message ServiceNameCollection {
    repeated ServiceNameElement elements = 1;
}

message ServiceNameMappingCollection {
    repeated ServiceNameMappingElement elements = 1;
}

message ServiceNameMappingElement {
    int32 serviceId = 1;
    ServiceNameElement element = 2;
}

message ServiceNameElement {
    string serviceName = 1;
    int32 applicationId = 2;
}
```
- 可选服务，可有效降低网络消耗，推荐实现。注意，由于部分应用存在URI中夹带参数的情况，请注意限制探针内的缓存容量，防止内存溢出。
- ServiceNameElement中，applicationId为当前applicationCode对应的id。serviceName一般为对应span的operationName

## JVM指标上报服务
### 简介
上报当前实例的JVM信息，每秒上报一次。

### 协议类型
gRPC服务

### 协议内容
https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/proto/JVMMetricsService.proto
```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.proto";

import "Downstream.proto";

service JVMMetricsService {
    rpc collect (JVMMetrics) returns (Downstream) {
    }
}

message JVMMetrics {
    repeated JVMMetric metrics = 1;
    int64 applicationInstanceId = 2;
}

message JVMMetric {
    int64 time = 1;
    CPU cpu = 2;
    repeated Memory memory = 3;
    repeated MemoryPool memoryPool = 4;
    repeated GC gc = 5;
}

message CPU {
    double usagePercent = 2;
}

message Memory {
    bool isHeap = 1;
    int64 init = 2;
    int64 max = 3;
    int64 used = 4;
    int64 committed = 5;
}

message MemoryPool {
    PoolType type = 1;
    bool isHeap = 2;
    int64 init = 3;
    int64 max = 4;
    int64 used = 5;
    int64 commited = 6;
}

enum PoolType {
    CODE_CACHE_USAGE = 0;
    NEWGEN_USAGE = 1;
    OLDGEN_USAGE = 2;
    SURVIVOR_USAGE = 3;
    PERMGEN_USAGE = 4;
    METASPACE_USAGE = 5;
}

message GC {
    GCPhrase phrase = 1;
    int64 count = 2;
    int64 time = 3;
}

enum GCPhrase {
    NEW = 0;
    OLD = 1;
}
```

## TraceSegment上报服务
### 简介
上报调用链信息

### 协议类型
gRPC服务

### 协议内容
```proto
syntax = "proto3";

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.proto";

import "Downstream.proto";
import "KeyWithStringValue.proto";

service TraceSegmentService {
    rpc collect (stream UpstreamSegment) returns (Downstream) {
    }
}

message UpstreamSegment {
    repeated UniqueId globalTraceIds = 1;
    bytes segment = 2; // the byte array of TraceSegmentObject
}

message UniqueId {
    repeated int64 idParts = 1;
}

message TraceSegmentObject {
    UniqueId traceSegmentId = 1;
    repeated TraceSegmentReference refs = 2;
    repeated SpanObject spans = 3;
    int32 applicationId = 4;
    int32 applicationInstanceId = 5;
}

message TraceSegmentReference {
    RefType refType = 1;
    UniqueId parentTraceSegmentId = 2;
    int32 parentSpanId = 3;
    int32 parentApplicationInstanceId = 4;
    string networkAddress = 5;
    int32 networkAddressId = 6;
    string entryServiceName = 7;
    int32 entryServiceId = 8;
    string parentServiceName = 9;
    int32 parentServiceId = 10;
}

message SpanObject {
    int32 spanId = 1;
    int32 parentSpanId = 2;
    int64 startTime = 3;
    int64 endTime = 4;
    int32 operationNameId = 5;
    string operationName = 6;
    int32 peerId = 7;
    string peer = 8;
    SpanType spanType = 9;
    SpanLayer spanLayer = 10;
    int32 componentId = 11;
    string component = 12;
    bool isError = 13;
    repeated KeyWithStringValue tags = 14;
    repeated LogMessage logs = 15;
}

enum RefType {
    CrossProcess = 0;
    CrossThread = 1;
}

enum SpanType {
    Entry = 0;
    Exit = 1;
    Local = 2;
}

enum SpanLayer {
    Database = 0;
    RPCFramework = 1;
    Http = 2;
    MQ = 3;
}

message LogMessage {
    int64 time = 1;
    repeated KeyWithStringValue data = 2;
}
```
- UniqueId为segment或者globalTraceId的数字表示。由3个long组成，1）applicationInstanceId，2）当前线程id，3）当前时间戳*10000 + seq(0-10000自循环)
- Span的数据，请参考[插件开发规范](https://github.com/apache/incubator-skywalking/wiki/Plugin-Development-Guide)
- 以下id和名称根据注册返回结果，优先上报id，无法获取id时，再上传name。参考之前的应用和服务注册章节。
  - operationNameId/operationName 
  - networkAddress/networkAddressId
  - entryServiceName/entryServiceId
  - parentServiceName/parentServiceId
  - peerId/peer
- componentId为默认支持的插件id，非官方支持，需传输名称或修改服务端源代码。[官方组件列表](https://github.com/apache/incubator-skywalking/blob/master/apm-network/src/main/java/org.apache.skywalking.apm/network/trace/component/ComponentsDefine.java)
