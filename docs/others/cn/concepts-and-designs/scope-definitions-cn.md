# 作用域（Scopes）和 字段（Fields）
通过使用聚合函数, 请求将按时间分组, 并且在每个作用域中都有**Group Key(s)**。


### `All`作用域

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| endpoint  | 表示每次请求的端点。  |   | string |
| latency  | 表示每次请求的延时。 |   |  int(in ms)  |
| status  | 表示每次请求是成功或失败。  |   | bool(true for success)  |
| responseCode | 如果是HTTP请求，表示响应的状态码。比如：200, 404, 302| | int |

### `Service`作用域

计算服务的每个请求中的度量数据。

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示服务的唯一ID | yes | int |
| name | 表示服务的名称 | | string |
| serviceInstanceName | 表示引用的服务实例id的名称 | | string |
| endpointName | 表示端点的名称, 比如HTTP URI的全路径  | | string |
| latency | 表示每次请求的延时 | | int |
| status | 表示每次请求成功或失败 | | bool(true for success)  |
| responseCode | 如果是HTTP请求，表示响应的状态码。比如：200, 404, 302 | | int|
| type | 表示请求的类型，比如: Database, HTTP, RPC, gRPC. | | enum |

### `ServiceInstance`作用域

计算服务实例的每个请求中的度量数据。

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示服务实例的唯一ID，通常是一个数 | yes | int |
| name |  表示服务实例的名称. 比如`ip:port@Service Name`.  **注意**: 目前原生的探针使用 `processId@Service name` 作为实例名称, 当您要在聚合中设置筛选器时, 这是无效的。 | | string|
| serviceName | 表示服务名 | | string |
| endpointName | 表示端点的名称, 比如HTTP URI的全路径. | | string|
| latency | 表示每次请求的延时. | | int |
| status | 表示每次请求成功或失败. | | bool(true for success) |
| responseCode | 如果是HTTP请求，表示响应的状态码。 | | int |
| type | 表示请求的类型，比如: Database, HTTP, RPC, gRPC. | | enum |

#### `ServiceInstance`的二级作用域 

如果服务实例是 jvm 并由 javaagent 收集, 则计算度量数据。

1. `ServiceInstanceJVMCPU`作用域

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示服务实例的唯一ID，通常是一个数 | yes | int |
| name |  表示服务实例的名称. 比如`ip:port@Service Name`.  **注意**: 目前原生的探针使用 `processId@Service name` 作为实例名称, 当您要在聚合中设置筛选器时, 这是无效的。 | | string|
| serviceName | 表示服务名 | | string |
| usePercent | 表示消耗cpu时间的百分比值 | | double|

2. `ServiceInstanceJVMMemory` 作用域

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示服务实例的唯一ID，通常是一个数. | yes | int |
| name |  表示服务实例的名称. 比如`ip:port@Service Name`.  **注意**: 目前原生的探针使用 `processId@Service name` 作为实例名称, 当您要在聚合中设置筛选器时, 这是无效的。 | | string|
| serviceName | 表示服务名. | | string |
| heapStatus | 表示此值的内存度量值是堆或非堆 | | bool |
| init | 参考JVM文档 | | long |
| max | 参考JVM文档 | | long |
| used | 参考JVM文档 | | long |
| committed | 参考JVM文档 | | long |

3. `ServiceInstanceJVMMemoryPool`作用域

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示服务实例的唯一ID，通常是一个数. | yes | int |
| name |  表示服务实例的名称. 比如`ip:port@Service Name`.  **注意**: 目前原生的探针使用 `processId@Service name` 作为实例名称, 当您要在聚合中设置筛选器时, 这是无效的。 | | string|
| serviceName | 表示服务名. | | string |
| poolType | 基于不同版本的JVM包括CODE_CACHE_USAGE, NEWGEN_USAGE, OLDGEN_USAGE, SURVIVOR_USAGE, PERMGEN_USAGE, METASPACE_USAGE | | enum |
| init | 参考JVM文档 | | long |
| max | 参考JVM文档 | | long |
| used | 参考JVM文档 | | long |
| committed | 参考JVM文档 | | long |

4. `ServiceInstanceJVMGC`作用域

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示服务实例的唯一ID，通常是一个数. | yes | int |
| name |  表示服务实例的名称. 比如`ip:port@Service Name`.  **注意**: 目前原生的探针使用 `processId@Service name` 作为实例名称, 当您要在聚合中设置筛选器时, 这是无效的。 | | string|
| serviceName | 表示服务名. | | string |
| phrase | 包括NEW和OLD | | Enum |
| time | GC消耗时间 | | long |
| count | GC操作的次数 | | long |

### `Endpoint`作用域

计算服务中端点的每个请求的度量数据。

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | 表示端点的唯一ID，通常是一个数. | yes | int |
| name | 表示端点的名称, 比如HTTP URI的全路径. | | string |
| serviceName | 表示服务名. | | string |
| serviceInstanceName | 表示引用的服务实例id的名称 | | string |
| latency | 表示每次请求的延时. | | int |
| status | 表示每次请求成功或失败. | | bool(true for success) |
| responseCode | 如果是HTTP请求，表示响应的状态码。 | | int |
| type | 表示请求的类型，比如: Database, HTTP, RPC, gRPC. | | enum |

### `ServiceRelation`作用域

计算每个请求中，一个服务和另一个服务之间的度量数据。

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| sourceServiceId | 表示来源服务的id. | yes | int |
| sourceServiceName | 表示来源服务的名称 | | string |
| sourceServiceInstanceName | 表示来源服务实例的名称 | | string |
| destServiceId | 表示目的服务的id | yes | string |
| destServiceName | 表示目的服务的名称. | | string |
| destServiceInstanceName | 表示目的服务实例的名称.| | string|
| endpoint | 表示在此次调用中使用的端点. | | string
| componentId | 表示在此次调用中使用的组件的ID. | yes | string
| latency | 表示每次请求的延时. | | int |
| status | 表示每次请求成功或失败. | | bool(true for success) |
| responseCode | 如果是HTTP请求，表示响应的状态码。| | int |
| type | 表示请求的类型，比如: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | 表示检测到的关系所在位置。比如: 客户端、服务器或者代理。 | yes | enum|

### `ServiceInstanceRelation`作用域

计算每个请求中，一个服务实例和另一个服务实例之间的指标数据

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| sourceServiceInstanceId | 表示来源服务实例的id. | yes | int|
| sourceServiceName | 表示来源服务的名称. | | string |
| sourceServiceInstanceName | 表示来源服务实例的名称. | | string |
| destServiceName | 表示目的服务名称. | | |
| destServiceInstanceId | 表示目的服务实例的id. | yes | int| 
| destServiceInstanceName | 表示目的服务实例的名称. | | string |
| endpoint | 表示在此次调用中使用的端点. | | string
| componentId | 表示在此次调用中使用的组件的ID. | yes | string
| latency | 表示每次请求的延时. | | int |
| status | 表示每次请求成功或失败. | | bool(true for success) |
| responseCode | 如果是HTTP请求，表示响应的状态码。| | int |
| type | 表示请求的类型，比如: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | 表示检测到的关系所在位置。比如: 客户端、服务器或者代理。 | yes | enum|

### `EndpointRelation`作用域

计算一个端点和另一个端点之间依赖关系的指标数据。
这种关系很难检测, 同样取决于传播上一端点的追踪库。
因此, `EndpointRelation`作用域聚合仅在由SkyWalking原生追踪下的服务中生效,
包括自动埋点探针(如java、.net)、OpenCensus SkyWalking exporter的实现或其他依据SkyWalking规范传播的追踪上下文。

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| endpointId | 表示依赖中为父节点的端点的id | yes | int |
| endpoint | 表示依赖中为父节点的端点| | string| 
| childEndpointId | 表示在第一行中依赖中作为子节点的端点id | yes | int| 
| childEndpoint| 表示在第二行中依赖中作为子节点的端点 | | string |
| rpcLatency | 表示端点到子端点中rpc的耗时。除去父端点本身的耗时。
| componentId | 表示在此次调用中使用的组件的ID. | yes | string
| status | 表示每次请求成功或失败. | | bool(true for success) |
| responseCode | 如果是HTTP请求，表示响应的状态码。| | int |
| type | 表示请求的类型，比如: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | 表示检测到的关系所在位置。比如: 客户端、服务器或者代理。 | yes | enum|
