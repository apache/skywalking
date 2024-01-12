# Scopes and Fields

Using the Aggregation Function, the requests will be grouped by time and **Group Key(s)** in each scope.

### SCOPE `Service`

This calculates the metrics data from each request of the service.

| Name                      | Remarks                                                                                                                         | Group Key | Type                   |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------|
| name                      | The name of the service.                                                                                                        |           | string                 |
| layer                     | Layer represents an abstract framework in the computer science, such as operation system(OS_LINUX layer), Kubernetes(k8s layer) |           | enum                   |
| serviceInstanceName       | The name of the service instance ID.                                                                                            |           | string                 |
| endpointName              | The name of the endpoint, such as a full path of HTTP URI.                                                                      |           | string                 |
| latency                   | The time taken by each request.                                                                                                 |           | int                    |
| status                    | Indicates the success or failure of the request.                                                                                |           | bool(true for success) |
| httpResponseStatusCode    | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302                                |           | int                    |
| rpcStatusCode             | The string value of the rpc response code.                                                                                      |           | string                 |
| type                      | The type of each request. Such as: Database, HTTP, RPC, gRPC.                                                                   |           | enum                   |
| tags                      | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment.                                          |           | `List<String>`         |
| tag                       | The key-value pair of span tags in the segment.                                                                                 |           | `Map<String, String>`  |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                        |           | string                 |

### SCOPE `TCPService`

This calculates the metrics data from each request of the TCP service.

| Name                      | Remarks                                                                                                                         | Group Key | Type                  |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------|-----------|-----------------------|
| name                      | The name of the service.                                                                                                        |           | string                |
| layer                     | Layer represents an abstract framework in the computer science, such as operation system(OS_LINUX layer), Kubernetes(k8s layer) |           | enum                  |
| serviceInstanceName       | The name of the service instance ID.                                                                                            |           | string                |
| tags                      | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment.                                          |           | `List<String>`        |
| tag                       | The key-value pair of span tags in the segment.                                                                                 |           | `Map<String, String>` |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                        |           | string                |
| receivedBytes             | The received bytes of the TCP traffic.                                                                                          |           | long                  |
| sentBytes                 | The sent bytes of the TCP traffic.                                                                                              |           | long                  |

### SCOPE `ServiceInstance`

This calculates the metrics data from each request of the service instance.

| Name                      | Remarks                                                                                                                                                                                                      | Group Key | Type                   |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------|
| name                      | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string                 |
| serviceName               | The name of the service.                                                                                                                                                                                     |           | string                 |
| endpointName              | The name of the endpoint, such as a full path of the HTTP URI.                                                                                                                                               |           | string                 |
| latency                   | The time taken by each request.                                                                                                                                                                              |           | int                    |
| status                    | Indicates the success or failure of the request.                                                                                                                                                             |           | bool(true for success) |
| httpResponseStatusCode    | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302                                                                                                             |           | int                    |
| rpcStatusCode             | The string value of the rpc response code.                                                                                                                                                                   |           | string                 |
| type                      | The type of each request, such as Database, HTTP, RPC, or gRPC.                                                                                                                                              |           | enum                   |
| tags                      | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment.                                                                                                                       |           | `List<String>`         |
| tag                       | The key-value pair of span tags in the segment.                                                                                                                                                              |           | `Map<String, String>`  |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                                                                                                     |           | string                 |

### SCOPE `TCPServiceInstance`

This calculates the metrics data from each request of the service instance.

| Name                      | Remarks                                                                                                                                                                                                      | Group Key | Type                  |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|-----------------------|
| name                      | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string                |
| serviceName               | The name of the service.                                                                                                                                                                                     |           | string                |
| tags                      | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment.                                                                                                                       |           | `List<String>`        |
| tag                       | The key-value pair of span tags in the segment.                                                                                                                                                              |           | `Map<String, String>` |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                                                                                                     |           | string                |
| receivedBytes             | The received bytes of the TCP traffic.                                                                                                                                                                       |           | long                  |
| sentBytes                 | The sent bytes of the TCP traffic.                                                                                                                                                                           |           | long                  |

#### Secondary scopes of `ServiceInstance`

This calculates the metrics data if the service instance is a JVM and collects through javaagent.

1. SCOPE `ServiceInstanceJVMCPU`

| Name        | Remarks                                                                                                                                                                                                      | Group Key | Type   |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| name        | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string |
| serviceName | The name of the service.                                                                                                                                                                                     |           | string |
| usePercent  | The percentage of CPU time spent.                                                                                                                                                                            |           | double |

2. SCOPE `ServiceInstanceJVMMemory`

| Name        | Remarks                                                                                                                                                                                                      | Group Key | Type   |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| name        | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string |
| serviceName | The name of the service.                                                                                                                                                                                     |           | string |
| heapStatus  | Indicates whether the metric has a heap property or not.                                                                                                                                                     |           | bool   |
| init        | See the JVM documentation.                                                                                                                                                                                   |           | long   |
| max         | See the JVM documentation.                                                                                                                                                                                   |           | long   |
| used        | See the JVM documentation.                                                                                                                                                                                   |           | long   |
| committed   | See the JVM documentation.                                                                                                                                                                                   |           | long   |

3. SCOPE `ServiceInstanceJVMMemoryPool`

| Name        | Remarks                                                                                                                                                                                                      | Group Key | Type   |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| name        | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string |
| serviceName | The name of the service.                                                                                                                                                                                     |           | string |
| poolType    | The type may be CODE_CACHE_USAGE, NEWGEN_USAGE, OLDGEN_USAGE, SURVIVOR_USAGE, PERMGEN_USAGE, or METASPACE_USAGE based on different versions of JVM.                                                          |           | enum   |
| init        | See the JVM documentation.                                                                                                                                                                                   |           | long   |
| max         | See the JVM documentation.                                                                                                                                                                                   |           | long   |
| used        | See the JVM documentation.                                                                                                                                                                                   |           | long   |
| committed   | See the JVM documentation.                                                                                                                                                                                   |           | long   |

4. SCOPE `ServiceInstanceJVMGC`

| Name        | Remarks                                                                                                                                                                                                      | Group Key | Type   |
|-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| name        | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string |
| serviceName | The name of the service.                                                                                                                                                                                     |           | string |
| phase       | Includes both NEW and OLD.                                                                                                                                                                                   |           | Enum   |
| time        | The time spent in GC.                                                                                                                                                                                        |           | long   |
| count       | The count in GC operations.                                                                                                                                                                                  |           | long   |

5. SCOPE `ServiceInstanceJVMThread`

| Name                         | Remarks                                                                                                                                                                                                      | Group Key | Type   |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| name                         | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string |
| serviceName                  | The name of the service.                                                                                                                                                                                     |           | string |
| liveCount                    | The current number of live threads.                                                                                                                                                                          |           | long   |
| daemonCount                  | The current number of daemon threads.                                                                                                                                                                        |           | long   |
| peakCount                    | The current number of peak threads.                                                                                                                                                                          |           | long   |
| runnableStateThreadCount     | The current number of threads in runnable state.                                                                                                                                                             |           | long   |
| blockedStateThreadCount      | The current number of threads in blocked state.                                                                                                                                                              |           | long   |
| waitingStateThreadCount      | The current number of threads in waiting state.                                                                                                                                                              |           | long   |
| timedWaitingStateThreadCount | The current number of threads in time-waiting state.                                                                                                                                                         |           | long   |

6. SCOPE `ServiceInstanceJVMClass`

| Name                    | Remarks                                                                                                                                                                                                      | Group Key | Type   |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| name                    | The name of the service instance, such as `ip:port@Service Name`.  **Note**: Currently, the native agent uses `uuid@ipv4` as the instance name, which does not assist in setting up a filter in aggregation. |           | string |
| serviceName             | The name of the service.                                                                                                                                                                                     |           | string |
| loadedClassCount        | The number of classes that are currently loaded in the JVM.                                                                                                                                                  |           | long   |
| totalUnloadedClassCount | The total number of classes unloaded since the JVM has started execution.                                                                                                                                    |           | long   |
| totalLoadedClassCount   | The total number of classes that have been loaded since the JVM has started execution.                                                                                                                       |           | long   |

### SCOPE `Endpoint`

This calculates the metrics data from each request of the endpoint in the service.

| Name                      | Remarks                                                                                                   | Group Key | Type                   |
|---------------------------|-----------------------------------------------------------------------------------------------------------|-----------|------------------------|
| name                      | The name of the endpoint, such as a full path of the HTTP URI.                                            |           | string                 |
| serviceName               | The name of the service.                                                                                  |           | string                 |
| serviceNodeType           | The type of node to which the Service or Network address belongs, such as Normal, Database, MQ, or Cache. |           | enum                   |
| serviceInstanceName       | The name of the service instance ID.                                                                      |           | string                 |
| latency                   | The time taken by each request.                                                                           |           | int                    |
| status                    | Indicates the success or failure of the request.                                                          |           | bool(true for success) |
| httpResponseStatusCode    | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302          |           | int                    |
| rpcStatusCode             | The string value of the rpc response code.                                                                |           | string                 |
| type                      | The type of each request, such as Database, HTTP, RPC, or gRPC.                                           |           | enum                   |
| tags                      | The labels of each request. Each value is made up by `TagKey:TagValue` in the segment.                    |           | `List<String>`         |
| tag                       | The key-value pair of span tags in the segment.                                                           |           | `Map<String, String>`  |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                  |           | string                 |

### SCOPE `ServiceRelation`

This calculates the metrics data from each request between services.

| Name                      | Remarks                                                                                                                                             | Group Key | Type                   |
|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------|
| sourceServiceName         | The name of the source service.                                                                                                                     |           | string                 |
| sourceServiceInstanceName | The name of the source service instance.                                                                                                            |           | string                 |
| sourceLayer               | The layer of the source service.                                                                                                                    |           | enum                   |
| destServiceName           | The name of the destination service.                                                                                                                |           | string                 |
| destServiceInstanceName   | The name of the destination service instance.                                                                                                       |           | string                 |
| destLayer                 | The layer of the destination service.                                                                                                               |           | enum                   |
| endpoint                  | The endpoint used in this call.                                                                                                                     |           | string                 |
| componentId               | The ID of component used in this call.                                                                                                              | yes       | string                 |
| latency                   | The time taken by each request.                                                                                                                     |           | int                    |
| status                    | Indicates the success or failure of the request.                                                                                                    |           | bool(true for success) |
| httpResponseStatusCode    | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302                                                    |           | int                    |
| rpcStatusCode             | The string value of the rpc response code.                                                                                                          |           | string                 |
| type                      | The type of each request, such as Database, HTTP, RPC, or gRPC.                                                                                     |           | enum                   |
| detectPoint               | Where the relation is detected. The value may be client, server, or proxy.                                                                          | yes       | enum                   |
| tlsMode                   | The TLS mode between source and destination services, such as `service_relation_mtls_cpm = from(ServiceRelation.*).filter(tlsMode == "mTLS").cpm()` |           | string                 |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                                            |           | string                 |

### SCOPE `TCPServiceRelation`

This calculates the metrics data from each request between services.

| Name                      | Remarks                                                                                                                                             | Group Key | Type                   |
|---------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------|
| sourceServiceName         | The name of the source service.                                                                                                                     |           | string                 |
| sourceServiceInstanceName | The name of the source service instance.                                                                                                            |           | string                 |
| sourceLayer               | The layer of the source service.                                                                                                                    |           | enum                   |
| destServiceName           | The name of the destination service.                                                                                                                |           | string                 |
| destServiceInstanceName   | The name of the destination service instance.                                                                                                       |           | string                 |
| destLayer                 | The layer of the destination service.                                                                                                               |           | enum                   |
| endpoint                  | The endpoint used in this call.                                                                                                                     |           | string                 |
| componentId               | The ID of component used in this call.                                                                                                              | yes       | string                 |
| latency                   | The time taken by each request.                                                                                                                     |           | int                    |
| status                    | Indicates the success or failure of the request.                                                                                                    |           | bool(true for success) |
| httpResponseStatusCode    | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302                                                    |           | int                    |
| rpcStatusCode             | The string value of the rpc response code.                                                                                                          |           | string                 |
| type                      | The type of each request, such as Database, HTTP, RPC, or gRPC.                                                                                     |           | enum                   |
| detectPoint               | Where the relation is detected. The value may be client, server, or proxy.                                                                          | yes       | enum                   |
| tlsMode                   | The TLS mode between source and destination services, such as `service_relation_mtls_cpm = from(ServiceRelation.*).filter(tlsMode == "mTLS").cpm()` |           | string                 |
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                                            |           | string                 |
| receivedBytes             | The received bytes of the TCP traffic.                                                                                                              |           | long                   |
| sentBytes                 | The sent bytes of the TCP traffic.                                                                                                                  |           | long                   |

### SCOPE `ServiceInstanceRelation`

This calculates the metrics data from each request between service instances.

| Name                      | Remarks                                                                                                                                                                       | Group Key | Type                   |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------|
| sourceServiceName         | The name of the source service.                                                                                                                                               |           | string                 |
| sourceServiceInstanceName | The name of the source service instance.                                                                                                                                      |           | string                 |
| sourceServiceLayer        | The layer of the source service.                                                                                                                                              |           | enum                   |
| destServiceName           | The name of the destination service.                                                                                                                                          |           |                        |
| destServiceInstanceName   | The name of the destination service instance.                                                                                                                                 |           | string                 |
| destServiceLayer          | The layer of the destination service.                                                                                                                                         |           | enum                   |
| endpoint                  | The endpoint used in this call.                                                                                                                                               |           | string                 |
| componentId               | The ID of the component used in this call.                                                                                                                                    | yes       | string                 |
| latency                   | The time taken by each request.                                                                                                                                               |           | int                    |
| status                    | Indicates the success or failure of the request.                                                                                                                              |           | bool(true for success) |
| httpResponseStatusCode    | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302                                                                              |           | int                    |
| rpcStatusCode             | The string value of the rpc response code.                                                                                                                                    |           | string                 |
| type                      | The type of each request, such as Database, HTTP, RPC, or gRPC.                                                                                                               |           | enum                   |
| detectPoint               | Where the relation is detected. The value may be client, server, or proxy.                                                                                                    | yes       | enum                   |
| tlsMode                   | The TLS mode between source and destination service instances, such as `service_instance_relation_mtls_cpm = from(ServiceInstanceRelation.*).filter(tlsMode == "mTLS").cpm()` |           | string                 | 
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                                                                      |           | string                 |

### SCOPE `TCPServiceInstanceRelation`

This calculates the metrics data from each request between service instances.

| Name                      | Remarks                                                                                                                                                                       | Group Key | Type   |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------|--------|
| sourceServiceName         | The name of the source service.                                                                                                                                               |           | string |
| sourceServiceInstanceName | The name of the source service instance.                                                                                                                                      |           | string |
| sourceServiceLayer        | The layer of the source service.                                                                                                                                              |           | enum   |
| destServiceName           | The name of the destination service.                                                                                                                                          |           |        |
| destServiceInstanceName   | The name of the destination service instance.                                                                                                                                 |           | string |
| destServiceLayer          | The layer of the destination service.                                                                                                                                         |           | enum   |
| componentId               | The ID of the component used in this call.                                                                                                                                    | yes       | string |
| detectPoint               | Where the relation is detected. The value may be client, server, or proxy.                                                                                                    | yes       | enum   |
| tlsMode                   | The TLS mode between source and destination service instances, such as `service_instance_relation_mtls_cpm = from(ServiceInstanceRelation.*).filter(tlsMode == "mTLS").cpm()` |           | string | 
| sideCar.internalErrorCode | The sidecar/gateway proxy internal error code. The value is based on the implementation.                                                                                      |           | string |
| receivedBytes             | The received bytes of the TCP traffic.                                                                                                                                        |           | long   |
| sentBytes                 | The sent bytes of the TCP traffic.                                                                                                                                            |           | long   |

### SCOPE `EndpointRelation`

This calculates the metrics data of the dependency between endpoints.
This relation is hard to detect, and it depends on the tracing library to propagate the previous endpoint.
Therefore, the `EndpointRelation` scope aggregation comes into effect only in services under tracing by SkyWalking
native agents,
including auto instrument agents (like Java and .NET), or other tracing context propagation in SkyWalking specification.

| Name                     | Remarks                                                                                                                           | Group Key | Type                   |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------|-----------|------------------------|
| endpoint                 | The parent endpoint in the dependency.                                                                                            |           | string                 |
| serviceName              | The name of the service.                                                                                                          |           | string                 |
| serviceNodeType          | The type of node to which the Service or Network address belongs, such as Normal, Database, MQ, or Cache.                         |           | enum                   |
| childEndpoint            | The endpoint used by the parent endpoint in row(1).                                                                               |           | string                 |
| childServiceName         | The endpoint used by the parent service in row(1).                                                                                |           | string                 |
| childServiceNodeType     | The type of node to which the Service or Network address belongs, such as Normal, Database, MQ, or Cache.                         |           | string                 |
| childServiceInstanceName | The endpoint used by the parent service instance in row(1).                                                                       |           | string                 |
| rpcLatency               | The latency of the RPC between the parent endpoint and childEndpoint, excluding the latency caused by the parent endpoint itself. 
| componentId              | The ID of the component used in this call.                                                                                        | yes       | string                 
| status                   | Indicates the success or failure of the request.                                                                                  |           | bool(true for success) |
| httpResponseStatusCode   | The response code of the HTTP response, and if this request is the HTTP call. E.g. 200, 404, 302                                  |           | int                    |
| rpcStatusCode            | The string value of the rpc response code.                                                                                        |           | string                 |
| type                     | The type of each request, such as Database, HTTP, RPC, or gRPC.                                                                   |           | enum                   |
| detectPoint              | Indicates where the relation is detected. The value may be client, server, or proxy.                                              | yes       | enum                   |

### SCOPE `BrowserAppTraffic`

This calculates the metrics data from each request of the browser application (browser only).

| Name            | Remarks                                                                        | Group Key | Type   |
|-----------------|--------------------------------------------------------------------------------|-----------|--------|
| name            | The browser application name of each request.                                  |           | string |
| count           | The number of request, which is fixed at 1.                                    |           | int    |
| trafficCategory | The traffic category. The value may be NORMAL, FIRST_ERROR, or ERROR.          |           | enum   |
| errorCategory   | The error category. The value may be AJAX, RESOURCE, VUE, PROMISE, or UNKNOWN. |           | enum   |

### SCOPE `BrowserAppSingleVersionTraffic`

This calculates the metrics data from each request of a single version in the browser application (browser only).

| Name            | Remarks                                                                        | Group Key | Type   |
|-----------------|--------------------------------------------------------------------------------|-----------|--------|
| name            | The single version name of each request.                                       |           | string |
| serviceName     | The name of the browser application.                                           |           | string |
| count           | The number of request, which is fixed at 1.                                    |           | int    |
| trafficCategory | The traffic category. The value may be NORMAL, FIRST_ERROR, or ERROR.          |           | enum   |
| errorCategory   | The error category. The value may be AJAX, RESOURCE, VUE, PROMISE, or UNKNOWN. |           | enum   |

### SCOPE `BrowserAppPageTraffic`

This calculates the metrics data from each request of the page in the browser application (browser only).

| Name            | Remarks                                                                        | Group Key | Type   |
|-----------------|--------------------------------------------------------------------------------|-----------|--------|
| name            | The page name of each request.                                                 |           | string |
| serviceName     | The name of the browser application.                                           |           | string |
| count           | The number of request, which is fixed at 1.                                    |           | int    |
| trafficCategory | The traffic category. The value may be NORMAL, FIRST_ERROR, or ERROR.          |           | enum   |
| errorCategory   | The error category. The value may be AJAX, RESOURCE, VUE, PROMISE, or UNKNOWN. |           | enum   |

### SCOPE `BrowserAppPagePerf`

This calculates the metrics data from each request of the page in the browser application (browser only).

| Name            | Remarks                                 | Group Key | Type       |
|-----------------|-----------------------------------------|-----------|------------|
| name            | The page name of each request.          |           | string     |
| serviceName     | The name of the browser application.    |           | string     |
| redirectTime    | The time taken to redirect.             |           | int(in ms) |
| dnsTime         | The DNS query time.                     |           | int(in ms) |
| ttfbTime        | Time to first byte.                     |           | int(in ms) |
| tcpTime         | TCP connection time.                    |           | int(in ms) |
| transTime       | Content transfer time.                  |           | int(in ms) |
| domAnalysisTime | Dom parsing time.                       |           | int(in ms) |
| fptTime         | First paint time or blank screen time.  |           | int(in ms) |
| domReadyTime    | Dom ready time.                         |           | int(in ms) |
| loadPageTime    | Page full load time.                    |           | int(in ms) |
| resTime         | Synchronous load resources in the page. |           | int(in ms) |
| sslTime         | Only valid for HTTPS.                   |           | int(in ms) |
| ttlTime         | Time to interact.                       |           | int(in ms) |
| firstPackTime   | First pack time.                        |           | int(in ms) |
| fmpTime         | First Meaningful Paint.                 |           | int(in ms) |

### SCOPE `Event`

This calculates the metrics data from [events](event.md).

| Name            | Remarks                                                                 | Group Key | Type   |
|-----------------|-------------------------------------------------------------------------|-----------|--------|
| name            | The name of the event.                                                  |           | string |
| service         | The service name to which the event belongs to.                         |           | string |
| serviceInstance | The service instance to which the event belongs to, if any.             |           | string |
| endpoint        | The service endpoint to which the event belongs to, if any.             |           | string |
| type            | The type of the event, `Normal` or `Error`.                             |           | string |
| message         | The message of the event.                                               |           | string |
| parameters      | The parameters in the `message`, see [parameters](event.md#parameters). |           | string |

### SCOPE `DatabaseAccess`

This calculates the metrics data from each request of database.

| Name           | Remarks                                          | Group Key | Type       |
|----------------|--------------------------------------------------|-----------|------------|
| name           | The service name of virtual database service.    |           | string     |
| databaseTypeId | The ID of the component used in this call.       |           | int        |
| latency        | The time taken by each request.                  |           | int(in ms) |
| status         | Indicates the success or failure of the request. |           | boolean    |

### SCOPE `DatabaseSlowStatement`

This calculates the metrics data from slow request of database.

| Name              | Remarks                                  | Group Key | Type       |
|-------------------|------------------------------------------|-----------|------------|
| databaseServiceId | The service id of virtual cache service. |           | string     |
| statement         | The sql statement .                      |           | string     |
| latency           | The time taken by each request.          |           | int(in ms) |
| traceId           | The traceId of this slow statement       |           | string     |

### SCOPE `CacheAccess`

This calculates the metrics data from each request of cache system.

| Name        | Remarks                                             | Group Key | Type       |
|-------------|-----------------------------------------------------|-----------|------------|
| name        | The service name of virtual cache service.          |           | string     |
| cacheTypeId | The ID of the component used in this call.          |           | int        |
| latency     | The time taken by each request.                     |           | int(in ms) |
| status      | Indicates the success or failure of the request.    |           | boolean    |
| operation   | Indicates this access is used for `write` or `read` |           | string     |

### SCOPE `CacheSlowAccess`

This calculates the metrics data from slow request of cache system , which is used for `write` or `read` operation.

| Name           | Remarks                                             | Group Key | Type       |
|----------------|-----------------------------------------------------|-----------|------------|
| cacheServiceId | The service id of virtual cache service.            |           | string     |
| command        | The cache command .                                 |           | string     |
| key            | The cache command key.                              |           | string     |
| latency        | The time taken by each request.                     |           | int(in ms) |
| traceId        | The traceId of this slow access                     |           | string     |
| status         | Indicates the success or failure of the request.    |           | boolean    |
| operation      | Indicates this access is used for `write` or `read` |           | string     |

### SCOPE `MQAccess`

This calculates the service dimensional metrics data from each request of MQ system on consume/produce side

| Name                | Remarks                                                 | Group Key | Type       |
|---------------------|---------------------------------------------------------|-----------|------------|
| name                | The service name , usually it's MQ address(es)          |           | string     |
| transmissionLatency | The latency from produce side to consume side .         |           | int(in ms) |
| status              | Indicates the success or failure of the request.        |           | boolean    |
| operation           | Indicates this access is on `Produce` or `Consume` side |           | enum       |

### SCOPE `MQEndpointAccess`

This calculates the endpoint dimensional metrics data from each request of MQ system on consume/produce side

| Name                | Remarks                                                      | Group Key | Type       |
|---------------------|--------------------------------------------------------------|-----------|------------|
| serviceName         | The service name that this endpoint belongs to.              |           | string     |
| endpoint            | The endpoint name , usually it's combined by `queue`,`topic` |           | string     |
| transmissionLatency | The latency from produce side to consume side .              |           | int(in ms) |
| status              | Indicates the success or failure of the request.             |           | boolean    |
| operation           | Indicates this access is on `Produce` or `Consume` side      |           | enum       |

### SCOPES with `K8S` Prefix

All metrics starting with `K8S` are derived from monitoring the Access Log in Kubernetes.

#### Service, Service Instance and relations

For all `K8SService`, `K8SInstance`, `K8SServiceRelation` and `K8SServiceInstanceRelation`, they all have the 
following **package**/**protocol** level metric contents.

| Name                                | Remarks                                                                                                                                     | Group Key | Type                 |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|-----------|----------------------|
| type                                | The metrics from log type, the following names should have the type prefix. The value may be connect, accept, close, write, read, protocol. |           | string               |
| connect.duration                    | Connect to other service use duration.                                                                                                      |           | long(in nanoseconds) |
| connect.success                     | The connect is success or not.                                                                                                              |           | boolean              |
| accept.duration                     | Accept connection from client use duration.                                                                                                 |           | long(in nanoseconds) |
| close.duration                      | Close one connection use duration.                                                                                                          |           | long(in nanoseconds) |
| close.success                       | Close one connection is success or not.                                                                                                     |           | boolean              |
| write.duration                      | Write data to the connection use duration.                                                                                                  |           | long(in nanoseconds) |
| write.syscall                       | Write data to the connection syscall name. The value should be Write, Writev, Send, SendTo, SendMsg, SendMmsg, SendFile, SendFile64.        |           | string               |
| write.l4.duration                   | Write data to the connection use duration on Linux Layer 4.                                                                                 |           | long(in nanoseconds) |
| write.l4.transmitPackageCount       | Total package count on write data to the connection.                                                                                        |           | long                 |
| write.l4.retransmitPackageCount     | Total retransmit package count on write data to the connection.                                                                             |           | long                 |
| write.l4.totalPackageSize           | Total transmit package size on write data to the connection.                                                                                |           | long(bytes)          |
| write.l3.duration                   | Write data to the connection use duration on Linux Layer 3.                                                                                 |           | long(in nanoseconds) |
| write.l3.localDuration              | Write data to the connection use local duration on Linux Layer 3.                                                                           |           | long(in nanoseconds) |
| write.l3.outputDuration             | Write data to the connection use output duration on Linux Layer 3.                                                                          |           | long(in nanoseconds) |
| write.l3.resolveMACCount            | Total resolve remote MAC address count on write data to the connection.                                                                     |           | long                 |
| write.l3.resolveMACDuration         | Total resolve remote MAC address use duration on write data to the connection.                                                              |           | long(in nanoseconds) |
| write.l3.netFilterCount             | Total do net filtering count on write data to the connection.                                                                               |           | long                 |
| write.l3.netFilterDuration          | Total do net filtering use duration on write data to the connection.                                                                        |           | long(in nanoseconds) |
| write.l2.duration                   | Write data to the connection use duration on Linux L2.                                                                                      |           | long(nanoseconds)    |
| write.l2.networkDeviceName          | The network device name on write data to the connection.                                                                                    |           | string               |
| write.l2.enterQueueBufferCount      | The write package count to the network device queue on write data to the connection.                                                        |           | long                 |
| write.l2.readySendDuration          | Total ready send buffer duration on write data to the connection.                                                                           |           | long(in nanoseconds) |
| write.l2.networkDeviceSendDuration  | Total network send buffer use duration on write data to the connection.                                                                     |           | long(in nanoseconds) |
| read.duration                       | Read data from the connection use duration.                                                                                                 |           | long(in nanoseconds) | 
| read.syscall                        | Read data from the connection syscall name. The value should Read, Readv, Recv, RecvFrom, RecvMsg, RecvMmsg.                                |           | string               |
| read.l4.duration                    | Read data to the connection use duration on Linux Layer 4.                                                                                  |           | long(in nanoseconds) | 
| read.l3.duration                    | Read data to the connection use duration on Linux Layer 3.                                                                                  |           | long(in nanoseconds) | 
| read.l3.rcvDuration                 | Read data to the connection use receive duration on Linux Layer 3.                                                                          |           | long(in nanoseconds) | 
| read.l3.localDuration               | Read data to the connection use local duration on Linux Layer 3.                                                                            |           | long(in nanoseconds) |
| read.l3.netFilterCount              | Total do net filtering count on read data from the connection.                                                                              |           | long                 |
| read.l3.netFilterDuration           | Total do net filtering use duration on read data from the connection.                                                                       |           | long(in nanoseconds) |
| read.l2.netDeviceName               | The network device name on read data from the connection.                                                                                   |           | string               | 
| read.l2.packageCount                | Total read package count on the connection.                                                                                                 |           | long                 |
| read.l2.totalPackageSize            | Total read package size on the connection.                                                                                                  |           | long(bytes)          |
| read.l2.packageToQueueDuration      | Total read package to the queue duration on the connection.                                                                                 |           | long(in nanoseconds) |
| read.l2.rcvPackageFromQueueDuration | Total read package from the queue duration on the connection.                                                                               |           | long(in nanoseconds) | 
| protocol.type                       | The protocol type name, the following names should have the type prefix. The value should be HTTP.                                          |           | string               |
| protocol.success                    | This protocol request and response is success or not.                                                                                       |           | boolean              | 
| protocol.http.latency               | The latency of HTTP response.                                                                                                               |           | long(in nanoseconds) |
| protocol.http.url                   | The url path of HTTP request.                                                                                                               |           | string               |
| protocol.http.method                | The method name of HTTP request.                                                                                                            |           | string               | 
| protocol.http.statusCode            | The response code of HTTP response.                                                                                                         |           | int                  | 
| protocol.http.sizeOfRequestHeader   | The header size of HTTP request.                                                                                                            |           | long(bytes)          |
| protocol.http.sizeOfRequestBody     | The body size of HTTP request.                                                                                                              |           | long(bytes)          |
| protocol.http.sizeOfResponseHeader  | The header size of HTTP response.                                                                                                           |           | long(bytes)          |
| protocol.http.sizeOfResponseBody    | The body size of HTTP response.                                                                                                             |           | long(bytes)          |

#### Endpoint and Endpoint Relation

For `K8SEndpoint` and `K8SEndpointRelation`, they only have the following **protocol** level metric contents.

| Name                                | Remarks                                                                                                                                     | Group Key | Type                 |
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|-----------|----------------------|
| protocol.type                       | The protocol type name, the following names should have the type prefix. The value should be HTTP.                                          |           | string               |
| protocol.success                    | This protocol request and response is success or not.                                                                                       |           | boolean              |
| protocol.http.latency               | The latency of HTTP response.                                                                                                               |           | long(in nanoseconds) |
| protocol.http.url                   | The url path of HTTP request.                                                                                                               |           | string               |
| protocol.http.method                | The method name of HTTP request.                                                                                                            |           | string               |
| protocol.http.statusCode            | The response code of HTTP response.                                                                                                         |           | int                  |
| protocol.http.sizeOfRequestHeader   | The header size of HTTP request.                                                                                                            |           | long(bytes)          |
| protocol.http.sizeOfRequestBody     | The body size of HTTP request.                                                                                                              |           | long(bytes)          |
| protocol.http.sizeOfResponseHeader  | The header size of HTTP response.                                                                                                           |           | long(bytes)          |
| protocol.http.sizeOfResponseBody    | The body size of HTTP response.                                                                                                             |           | long(bytes)          |

