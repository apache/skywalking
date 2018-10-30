# Scopes and Fields
By using Aggregation Function, the requests will group by time and **Group Key(s)** in each scope.

### SCOPE `All`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| endpoint  | Represent the endpoint path of each request.  |   | string |
| latency  | Represent how much time of each request. |   |  int(in ms)  |
| status  | Represent whether success or fail of the request.  |   | bool(true for success)  |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. e.g. 200, 404, 302| | int |


### SCOPE `Service`

Calculate the metric data from each request of the service. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service | yes | int |
| name | Represent the name of the service | | string |
| serviceInstanceName | Represent the name of the service instance id referred | | string |
| endpointName | Represent the name of the endpoint, such a full path of HTTP URI | | string |
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request. | | bool(true for success)  |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call | | int|
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |

### SCOPE `ServiceInstance`

Calculate the metric data from each request of the service instance. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service instance, usually a number. | yes | int |
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `processId@Service name` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| endpointName | Represent the name of the endpoint, such a full path of HTTP URI. | | string|
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request. | | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |

#### Secondary scopes of `ServiceInstance` 

Calculate the metric data if the service instance is a JVM and collected by javaagent.

1. SCOPE `ServiceInstanceJVMCPU`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service instance, usually a number. | yes | int |
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `processId@Service name` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| usePercent | Represent how much percent of cpu time cost| | double|

2. SCOPE `ServiceInstanceJVMMemory`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service instance, usually a number. | yes | int |
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `processId@Service name` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| heapStatus | Represent this value the memory metric values are heap or not | | bool |
| init | See JVM document | | long |
| max | See JVM document | | long |
| used | See JVM document | | long |
| committed | See JVM document | | long |

3. SCOPE `ServiceInstanceJVMMemoryPool`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service instance, usually a number. | yes | int |
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `processId@Service name` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| poolType | Include CODE_CACHE_USAGE, NEWGEN_USAGE, OLDGEN_USAGE, SURVIVOR_USAGE, PERMGEN_USAGE, METASPACE_USAGE based on different version of JVM. | | enum |
| init | See JVM document | | long |
| max | See JVM document | | long |
| used | See JVM document | | long |
| committed | See JVM document | | long |

4. SCOPE `ServiceInstanceJVMGC`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service instance, usually a number. | yes | int |
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `processId@Service name` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| phrase | Include NEW and OLD | | Enum |
| time | GC time cost | | long |
| count | Count of GC op | | long |

### SCOPE `Endpoint`

Calculate the metric data from each request of the endpoint in the service. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the endpoint, usually a number. | yes | int |
| name | Represent the name of the endpoint, such a full path of HTTP URI. | | string |
| serviceName | Represent the name of the service. | | string |
| serviceInstanceName | Represent the name of the service instance id referred. | | string |
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |

### SCOPE `ServiceRelation`

Calculate the metric data from each request between one service and the other service

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| sourceServiceId | Represent the id of the source service. | yes | int |
| sourceServiceName | Represent the name of the source service. | | string |
| sourceServiceInstanceName | Represent the name of the source service instance. | | string |
| destServiceId | Represent the id of the destination service. | yes | string |
| destServiceName | Represent the name of the destination service. | | string |
| destServiceInstanceName | Represent the name of the destination service instance.| | string|
| endpoint | Represent the endpoint used in this call. | | string
| componentId | Represent the id of component used in this call. | yes | string
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|


### SCOPE `ServiceInstanceRelation`

Calculate the metric data from each request between one service instance and the other service instance

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| sourceServiceInstanceId | Represent the id of the source service instance. | yes | int|
| sourceServiceName | Represent the name of the source service. | | string |
| sourceServiceInstanceName | Represent the name of the source service instance. | | string |
| destServiceName | Represent the name of the destination service. | | |
| destServiceInstanceId | Represent the id of the destination service instance. | yes | int| 
| destServiceInstanceName | Represent the name of the destination service instance. | | string |
| endpoint | Represent the endpoint used in this call. | | string
| componentId | Represent the id of component used in this call. | yes | string
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|

### SCOPE `EndpointRelation`

Calculate the metric data of the dependency between one endpoint and the other endpoint. 
This relation is hard to detect, also depends on tracing lib to propagate the prev endpoint. 
So `EndpointRelation` scope aggregation effects only in service under tracing by SkyWalking native agents, 
including auto instrument agents(like Java, .NET), OpenCensus SkyWalking exporter implementation or others propagate tracing context in SkyWalking spec.

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| endpointId | Represent the id of the endpoint as parent in the dependency. | yes | int |
| endpoint | Represent the endpoint as parent in the dependency.| | string| 
| childEndpointId | Represent the id of the endpoint being used by the parent endpoint in row(1) | yes | int| 
| childEndpoint| Represent the endpoint being used by the parent endpoint in row(2) | | string |
| rpcLatency | Represent the latency of the RPC from some codes in the endpoint to the childEndpoint. Exclude the latency caused by the endpoint(1) itself.
| componentId | Represent the id of component used in this call. | yes | string
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|
