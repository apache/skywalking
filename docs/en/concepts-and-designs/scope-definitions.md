# Scopes and Fields
Using the Aggregation Function, the requests will group by time and **Group Key(s)** in each scope.

### SCOPE `All`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name  | The service name of each request.  |   | string |
| serviceInstanceName  | The name of the service instance ID.  |   | string |
| endpoint  | The endpoint path of each request.  |   | string |
| latency  | The time taken by each request. |   |  int(in ms)  |
| status  | The success or failure of the request.  |   | bool(true for success)  |
| responseCode | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302| | int |
| type | The type of each request, such as Database, HTTP, RPC, or gRPC. | | enum |
| tags | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment. | | `List<String>` |

### SCOPE `Service`

This calculates the metrics data from each request of the service. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | The name of the service. | | string |
| nodeType | The kind of node to which the Service or Network address belongs, such as Normal, Database, MQ, or Cache. | | enum |
| serviceInstanceName | The name of the service instance ID. | | string |
| endpointName | The name of the endpoint, such as a full path of HTTP URI. | | string |
| latency | The time taken by each request. | | int |
| status | Indicates the success or failure of the request. | | bool(true for success)  |
| responseCode | The response code of the HTTP response, if this request is an HTTP call. | | int|
| type | The type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| tags | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment. | | `List<String>` |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation. | | string|

### SCOPE `ServiceInstance`

This calculates the metrics data from each request of the service instance. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which will not assist in setting up a filter in aggregation. | | string|
| serviceName | The name of the service. | | string |
| nodeType | The kind of node to which the Service or Network address belongs, such as Normal, Database, MQ, or Cache. | | enum |
| endpointName | The name of the endpoint, such as a full path of the HTTP URI. | | string|
| latency | The time taken by each request. | | int |
| status | Indicates the success or failure of the request. | | bool(true for success) |
| responseCode | The response code of HTTP response, if this request is an HTTP call. | | int |
| type | The type of each request, such as Database, HTTP, RPC, or gRPC. | | enum |
| tags | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment. | | `List<String>` |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation. | | string|

#### Secondary scopes of `ServiceInstance` 

This calculates the metrics data if the service instance is a JVM and collects through javaagent.

1. SCOPE `ServiceInstanceJVMCPU`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which will not assist in setting up a filter in aggregation. | | string|
| serviceName | The name of the service. | | string |
| usePercent | The percentage of CPU time spent.| | double|

2. SCOPE `ServiceInstanceJVMMemory`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which will not assist in setting up a filter in aggregation. | | string|
| serviceName | The name of the service. | | string |
| heapStatus | Indicates whether the metric has a heap property or not. | | bool |
| init | See the JVM documentation. | | long |
| max | See the JVM documentation. | | long |
| used | See the JVM documentation. | | long |
| committed | See the JVM documentation. | | long |

3. SCOPE `ServiceInstanceJVMMemoryPool`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which will not assist in setting up a filter in aggregation. | | string|
| serviceName | The name of the service. | | string |
| poolType | May be CODE_CACHE_USAGE, NEWGEN_USAGE, OLDGEN_USAGE, SURVIVOR_USAGE, PERMGEN_USAGE, or METASPACE_USAGE based on different versions of JVM. | | enum |
| init | See the JVM documentation. | | long |
| max | See the JVM documentation. | | long |
| used | See the JVM documentation. | | long |
| committed | See the JVM documentation. | | long |

4. SCOPE `ServiceInstanceJVMGC`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name |  The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as instance name, which will not assist in setting up a filter in aggregation. | | string|
| serviceName | The name of the service. | | string |
| phrase | Includes both NEW and OLD. | | Enum |
| time | The time spent in GC. | | long |
| count | The count in GC operations. | | long |

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

This calculates the metrics data from each request of the browser application (browser only).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | The browser application name of each request. |  | string |
| count | The number of request, which is fixed at 1. |  | int |
| trafficCategory | The traffic category. The value may be NORMAL, FIRST_ERROR, or ERROR. | | enum |
| errorCategory | The error category. The value may be AJAX, RESOURCE, VUE, PROMISE, or UNKNOWN. | | enum |

### SCOPE `BrowserAppSingleVersionTraffic`

This calculates the metrics data from each request of the browser single version in the browser application (browser only).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | The single version name of each request. |  | string |
| serviceName | The name of the browser application. | | string |
| count | The number of request, which is fixed at 1. |  | int |
| trafficCategory | The traffic category. The value may be NORMAL, FIRST_ERROR, or ERROR. | | enum |
| errorCategory | The error category. The value may be AJAX, RESOURCE, VUE, PROMISE, or UNKNOWN. | | enum |

### SCOPE `BrowserAppPageTraffic`

This calculates the metrics data from each request of the page in the browser application (browser only).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | The page name of each request. |  | string |
| serviceName | The name of the browser application. | | string |
| count | The number of request, which is fixed at 1. |  | int |
| trafficCategory | The traffic category. The value may be NORMAL, FIRST_ERROR, or ERROR. | | enum |
| errorCategory | The error category. The value may be AJAX, RESOURCE, VUE, PROMISE, or UNKNOWN. | | enum |


### SCOPE `BrowserAppPagePerf`

This calculates the metrics data form each request of the page in the browser application (browser only).

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| name | The page name of each request. |  | string |
| serviceName | The name of the browser application. | | string |
| redirectTime | The time of redirection. |  | int(in ms) |
| dnsTime | The DNS query time. | | int(in ms) |
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
