# Scopes and Fields
By using Aggregation Function, the requests will group by time and **Group Key(s)** in each scope.

### SCOPE `All`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name  | Represent the service name of each request.  |   | string |
| serviceInstanceName  | Represent the name of the service instance id referred.  |   | string |
| endpoint  | Represent the endpoint path of each request.  |   | string |
| latency  | Represent how much time of each request. |   |  int(in ms)  |
| status  | Represent whether success or fail of the request.  |   | bool(true for success)  |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. e.g. 200, 404, 302| | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| tags | Represent the labels of each request and each value is made up with the `TagKey:TagValue` in the segment. | | `List<String>` |

### SCOPE `Service`

Calculate the metrics data from each request of the service. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | Represent the name of the service | | string |
| nodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | enum |
| serviceInstanceName | Represent the name of the service instance id referred | | string |
| endpointName | Represent the name of the endpoint, such a full path of HTTP URI | | string |
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request. | | bool(true for success)  |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call | | int|
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| tags | Represent the labels of each request and each value is made up with the `TagKey:TagValue` in the segment. | | `List<String>` |
| sideCar.internalErrorCode | Represent the sidecar/gateway proxy internal error code, the value bases on the implementation. | | string|

### SCOPE `ServiceInstance`

Calculate the metrics data from each request of the service instance. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `uuid@ipv4` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| nodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | enum |
| endpointName | Represent the name of the endpoint, such a full path of HTTP URI. | | string|
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request. | | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| tags | Represent the labels of each request and each value is made up with the `TagKey:TagValue` in the segment. | | `List<String>` |
| sideCar.internalErrorCode | Represent the sidecar/gateway proxy internal error code, the value bases on the implementation. | | string|

#### Secondary scopes of `ServiceInstance` 

Calculate the metrics data if the service instance is a JVM and collected by javaagent.

1. SCOPE `ServiceInstanceJVMCPU`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `uuid@ipv4` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| usePercent | Represent how much percent of cpu time cost| | double|

2. SCOPE `ServiceInstanceJVMMemory`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `uuid@ipv4` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| heapStatus | Represent this value the memory metrics values are heap or not | | bool |
| init | See JVM document | | long |
| max | See JVM document | | long |
| used | See JVM document | | long |
| committed | See JVM document | | long |

3. SCOPE `ServiceInstanceJVMMemoryPool`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `uuid@ipv4` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| poolType | Include CODE_CACHE_USAGE, NEWGEN_USAGE, OLDGEN_USAGE, SURVIVOR_USAGE, PERMGEN_USAGE, METASPACE_USAGE based on different version of JVM. | | enum |
| init | See JVM document | | long |
| max | See JVM document | | long |
| used | See JVM document | | long |
| committed | See JVM document | | long |

4. SCOPE `ServiceInstanceJVMGC`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `uuid@ipv4` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| phrase | Include NEW and OLD | | Enum |
| time | GC time cost | | long |
| count | Count of GC op | | long |

5. SCOPE `ServiceInstanceJVMThread`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `uuid@ipv4` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| liveCount | Represent Current number of live threads | | int |
| daemonCount | Represent Current number of daemon threads | | int |
| peakCount | Represent Current number of peak threads | | int |

### SCOPE `Endpoint`

Calculate the metrics data from each request of the endpoint in the service. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | Represent the name of the endpoint, such a full path of HTTP URI. | | string |
| serviceName | Represent the name of the service. | | string |
| serviceNodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | enum |
| serviceInstanceName | Represent the name of the service instance id referred. | | string |
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| tags | Represent the labels of each request and each value is made up with the `TagKey:TagValue` in the segment. | | `List<String>` |
| sideCar.internalErrorCode | Represent the sidecar/gateway proxy internal error code, the value bases on the implementation. | | string|

### SCOPE `ServiceRelation`

Calculate the metrics data from each request between one service and the other service

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| sourceServiceName | Represent the name of the source service. | | string |
| sourceServiceNodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | enum |
| sourceServiceInstanceName | Represent the name of the source service instance. | | string |
| destServiceName | Represent the name of the destination service. | | string |
| destServiceNodeType | Represent which kind of node of Service or Network address represents to. | | enum |
| destServiceInstanceName | Represent the name of the destination service instance.| | string|
| endpoint | Represent the endpoint used in this call. | | string
| componentId | Represent the id of component used in this call. | yes | string
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|
| tlsMode | Represent TLS mode between source and destination services. For example `service_relation_mtls_cpm = from(ServiceRelation.*).filter(tlsMode == "mTLS").cpm()` || string|
| sideCar.internalErrorCode | Represent the sidecar/gateway proxy internal error code, the value bases on the implementation. | | string|


### SCOPE `ServiceInstanceRelation`

Calculate the metrics data from each request between one service instance and the other service instance

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| sourceServiceName | Represent the name of the source service. | | string |
| sourceServiceNodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | enum |
| sourceServiceInstanceName | Represent the name of the source service instance. | | string |
| destServiceName | Represent the name of the destination service. | | |
| destServiceNodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | string |
| destServiceInstanceName | Represent the name of the destination service instance. | | string |
| endpoint | Represent the endpoint used in this call. | | string
| componentId | Represent the id of component used in this call. | yes | string
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|
| tlsMode | Represent TLS mode between source and destination service instances. For example, `service_instance_relation_mtls_cpm = from(ServiceInstanceRelation.*).filter(tlsMode == "mTLS").cpm()` || string|
| sideCar.internalErrorCode | Represent the sidecar/gateway proxy internal error code, the value bases on the implementation. | | string|

### SCOPE `EndpointRelation`

Calculate the metrics data of the dependency between one endpoint and the other endpoint. 
This relation is hard to detect, also depends on tracing lib to propagate the prev endpoint. 
So `EndpointRelation` scope aggregation effects only in service under tracing by SkyWalking native agents, 
including auto instrument agents(like Java, .NET), OpenCensus SkyWalking exporter implementation or others propagate tracing context in SkyWalking spec.

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| endpoint | Represent the endpoint as parent in the dependency.| | string|
| serviceName | Represent the name of the service. | | string |
| serviceNodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | enum |
| childEndpoint| Represent the endpoint being used by the parent endpoint in row(1) | | string |
| childServiceName | Represent the endpoint being used by the parent service in row(1) | | string |
| childServiceNodeType | Represent which kind of node of Service or Network address represents to, Such as: Normal, Database, MQ, Cache. | | string |
| childServiceInstanceName | Represent the endpoint being used by the parent service instance in row(1) | | string |
| rpcLatency | Represent the latency of the RPC from some codes in the endpoint to the childEndpoint. Exclude the latency caused by the endpoint(1) itself.
| componentId | Represent the id of component used in this call. | yes | string
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|


### SCOPE `BrowserAppTraffic`

Calculate the metrics data form each request of the browser app (only browser).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | Represent the browser app name of each request. |  | string |
| count | Represents the number of request, fixed at 1. |  | int |
| trafficCategory | Represents traffic category, Values: NORMAL, FIRST_ERROR, ERROR | | enum |
| errorCategory | Represents error category, Values: AJAX, RESOURCE, VUE, PROMISE, UNKNOWN | | enum |

### SCOPE `BrowserAppSingleVersionTraffic`

Calculate the metrics data form each request of the browser single version in the browser app (only browser).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | Represent the single version name of each request. |  | string |
| serviceName | Represent the name of the browser app. | | string |
| count | Represents the number of request, fixed at 1. |  | int |
| trafficCategory | Represents traffic category, Values: NORMAL, FIRST_ERROR, ERROR | | enum |
| errorCategory | Represents error category, Values: AJAX, RESOURCE, VUE, PROMISE, UNKNOWN | | enum |

### SCOPE `BrowserAppPageTraffic`

Calculate the metrics data form each request of the page in the browser app (only browser).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | Represent the page name of each request. |  | string |
| serviceName | Represent the name of the browser app. | | string |
| count | Represents the number of request, fixed at 1. |  | int |
| trafficCategory | Represents the traffic category, Values: NORMAL, FIRST_ERROR, ERROR | | enum |
| errorCategory | Represents the error category, Values: AJAX, RESOURCE, VUE, PROMISE, UNKNOWN | | enum |


### SCOPE `BrowserAppPagePerf`

Calculate the metrics data form each request of the page in the browser app (only browser).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | Represent the page name of each request. |  | string |
| serviceName | Represent the name of the browser app. | | string |
| redirectTime | Represents the time of redirection. |  | int(in ms) |
| dnsTime | Represents the DNS query time. | | int(in ms) |
| ttfbTime | Time to first Byte. | | int(in ms) |
| tcpTime | TCP connection time. | | int(in ms) |
| transTime | Content transfer time.  | | int(in ms) |
| domAnalysisTime | Dom parsing time. | | int(in ms) |
| fptTime | First paint time or blank screen time. | | int(in ms) |
| domReadyTime | Dom ready time. | | int(in ms) |
| loadPageTime | Page full load time. | | int(in ms) |
| resTime | Synchronous load resources in the page. | | int(in ms) |
| sslTime | Only valid for HTTPS. | | int(in ms) |
| ttlTime | Time to interact. | | int(in ms) |
| firstPackTime | First pack time. | | int(in ms) |
| fmpTime | First Meaningful Paint. | | int(in ms) |