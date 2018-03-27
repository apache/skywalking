# Trace Data Protocol 中文
Trace Data Protocol协议，也就是探针与Collector间通讯协议

## 概述
此协议包含了Agent上行/下行数据的格式，可用于定制开发，或者探针的多语言扩展

### 协议版本
v1.1

### 协议类型
* 服务发现使用HTTP服务
* 注册和数据上行服务同时支持gRPC和HTTP JSON服务

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
应用注册服务，是将applicationCode，转换成数字ID的服务。
此服务会在后续的传输过程中，有效降低网络带宽需求。

### 协议内容
[gRPC service define](../..apm-protocol/apm-network/src/main/proto/ApplicationRegisterService.proto)

- applicationCode为客户端设置的应用名.
- applicationCode对应的返回id,在后续协议中，被称为applicationId。

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

## 地址注册发现服务
### 简介
地址注册发现服务，是将远程调用（RPC、MQ、DB...）地址的（addresses）替换为id的服务。

### 协议内容
[gRPC service define](../../apm-protocol/apm-network/src/main/proto/NetworkAddressRegisterService.proto)

- 可选服务，可有效降低网络消耗，推荐实现。
- NetworkAddresses中，addresses为被调方的地址（IP/HOST:PORT）多个地址使用逗号分隔

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

### 协议类型
HTTP JSON服务, 属性名与gRPC对应，属性解释详见gRPC协议说明，统一采用HTTP POST方式

### 实例注册服务
地址: http://ip:port/instance/register(default: localhost:12800) 

输入：
```
{
    ai: x, #applicationId
    au: "", #agentUUID
    rt: x, #registerTime
    oi: "", #osinfo
}
```

输出：
```
{
    ai: x, #applicationId
    ii: x, #applicationInstanceId
}
```

### 实例心跳服务
地址: http://ip:port/instance/heartbeat(default: localhost:12800) 

输入：
```
{
    "ii": x, #applicationInstanceId
    "ht": x #heartbeatTime, java timestamp format
}
```

输出：无

## 服务名注册发现服务
地址: http://ip:port/servicename/discovery(default: localhost:12800) 

输入：
```
{
    ai: x, #applicationId
    sn: "", #serviceName
    st: x, #srcSpanType
}
```

输出：
```
{
    si: x, #osinfo
    el: { #element
        ai: x, #applicationId
        sn: "", #serviceName
        st: x, #srcSpanType
    }
}
```

## JVM指标上报服务
### 暂无支持需求，java和c#的探针都采用gRPC的方式上报

## TraceSegment上报服务
### 一次可以发送多个Segment，采用JSON数组的形式
输入：
```
[
  {
    "gt": [[230150, 185809, 24040000]], //globalTraceIds 链路编码，与调用方相同
    "sg": { //TraceSegmentObject 
      "ts": [137150, 185809, 48780000], //traceSegmentId，新产生
      "ai": 2, //applicationId
      "ii": 3, //applicationInstanceId
      "ss": [ //SpanObject
        {
          "si": 0, //spanId
          "tv": 0, //SpanType
          "lv": 2, //SpanLayer
          "ps": -1, //parentSpanId
          "st": 1501858094726, //startTime
          "et": 1501858096804, //endTime
          "ci": 3, //componentId
          "cn": "", //component
          "oi": 0, //operationNameId
          "on": "org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()", //operationName
          "pi": 0, //peerId
          "pn": "", //peer
          "ie": false, //isError
          "rs": [ //TraceSegmentReference
            {
              "pts": [230150, 185809, 24040000], //parentTraceSegmentId, 上级的segment_id 一个应用中的一个实例在链路中产生的编号
              "pii": 2, //parentApplicationInstanceId, 上级的实例编号
              "psp": 1, //parentSpanId, 上级的埋点编号span_id
              "psi": 0, //parentServiceId, 上级的服务编号(org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()注册后的ID)
              "psn": "/dubbox-case/case/dubbox-rest", //parentServiceName, 上级的服务名
              "ni": 0,  //networkAddressId, 上级调用时使用的地址注册后的ID
              "nn": "172.25.0.4:20880", //networkAddress, 上级的地址
              "eii": 2, //entryApplicationInstanceId, 入口的实例编号
              "esi": 0, //entryServiceId, 入口的服务编号
              "esn": "/dubbox-case/case/dubbox-rest", //entryServiceName, 入口的服务名词
              "rn": 0 //RefType, 调用方式（CrossProcess，CrossThread）
            }
          ],
          "to": [ //KeyWithStringValue
            {
              "k": "url", //key
              "v": "rest://172.25.0.4:20880/org.skywaking.apm.testcase.dubbo.services.GreetService.doBusiness()" //value
            },
            {
              "k": "http.method",
              "v": "GET"
            }
          ],
          "lo": { //LogMessage
            "t": 1501858094726,
            "d": [
                "k": "NullPointException",
                "v": "Error Stack"
            }
          }
        },
        {
          "si": 1,
          "tv": 1,
          "lv": 1,
          "ps": 0,
          "st": 1501858094726,
          "et": 1501858095804,
          "ci": 9,
          "cn": "",
          "oi": 0,
          "on": "mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]",
          "pi": 0,
          "pn": "localhost:27017",
          "ie": false,
          "to": [],
          "lo": []
        }
      ]
    }
  }
]
```
