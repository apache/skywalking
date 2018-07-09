# Observability Analysis Language
Provide OAL(Observability Analysis Language) to analysis incoming data in streaming mode. 

OAL focuses on metric in Service, Service Instance and Endpoint. Because of that, the language is easy to 
learn and use.

Considering performance, reading and debugging, OAL is defined as a compile language. 
The OAL scrips will be compiled to normal Java codes in package stage.

## Grammar
Scripts should be named as `*.oal`
```

METRIC_NAME = from(SCOPE.(* | [FIELD][,FIELD ...]))
[.filter(FIELD OP [INT | STRING])]
.FUNCTION([PARAM][, PARAM ...])
```

## Scope
**SCOPE** in (`All`, `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, `EndpointRelation`).

## Field
By using Aggregation Function, the requests will group by time and **Group Key(s)** in each scope.

- SCOPE `All`

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| endpoint  | Represent the endpoint path of each request.  |   | string |
| latency  | Represent how much time of each request. |   |  int(in ms)  |
| status  | Represent whether success or fail of the request.  |   | bool(true for success)  |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. e.g. 200, 404, 302| | int |


- SCOPE `Service`

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

- SCOPE `ServiceInstance`

Calculate the metric data from each request of the service instance. 

| Name | Remarks | Group Key | Type | 
|---|---|---|---|
| id | Represent the unique id of the service, usually a number. | yes | int |
| name |  Represent the name of the service instance. Such as `ip:port@Service Name`.  **Notice**: current native agent uses `processId@Service name` as instance name, which is useless when you want to setup a filter in aggregation. | | string|
| serviceName | Represent the name of the service. | | string |
| endpointName | Represent the name of the endpoint, such a full path of HTTP URI. | | string|
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request. | | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |

- SCOPE `Endpoint`

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

- SCOPE `ServiceRelation`

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
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|


- SCOPE `ServiceInstanceRelation`

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
| latency | Represent how much time of each request. | | int |
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|

- SCOPE `EndpointRelation`

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
| status | Represent whether success or fail of the request.| | bool(true for success) |
| responseCode | Represent the response code of HTTP response, if this request is the HTTP call. | | int |
| type | Represent the type of each request. Such as: Database, HTTP, RPC, gRPC. | | enum |
| detectPoint | Represent where is the relation detected. Values: client, server, proxy. | yes | enum|

## Filter
Use filter to build the conditions for the value of fields, by using field name and expression. 

The expressions support to link by `and`, `or` and `(...)`. 
The OPs support `=`, `!=`, `>`, `<`, `in (v1, v2, ...`, `like "%..."`, with type detection based of field type. Trigger compile
 or code generation error if incompatible. 

## Aggregation Function
The default functions are provided by SkyWalking OAP core, and could implement more. 

Provided functions
- `avg()`. The average value. The field type must be number.
- `p99()`. The 99% of the given values should be greater or equal. The field type must be number.
- `p90()`. The 90% of the given values should be greater or equal. The field type must be number.
- `p75()`. The 75% of the given values should be greater or equal. The field type must be number.
- `p50()`. The 75% of the given values should be greater or equal. The field type must be number.
- `percent()`. The percentage of selected by filter in the whole given data. No type requirement.
- `histogram(start, step)`. Group the given value by the given step, begin with the start value.
- `sum()`. The sum number of selected by filter. No type requirement.

## Metric name
The metric name for storage implementor, alarm and query modules. The type inference supported by core.

## Group
All metric data will be grouped by Scope.ID and min-level TimeBucket. 

- In `Endpoint` scope, the Scope.ID = Endpoint id (the unique id based on service and its Endpoint)

## Examples
```
// Caculate p99 of both Endpoint1 and Endpoint2
Endpoint_p99 = from(Endpoint.latency).filter(name in ("Endpoint1", "Endpoint2")).summary(0.99)

// Caculate p99 of Endpoint name started with `serv`
serv_Endpoint_p99 = from(Endpoint.latency).filter(name like ("serv%")).summary(0.99)

// Caculate the avg response time of each Endpoint
Endpoint_avg = from(Endpoint.latency).avg()

// Caculate the histogram of each Endpoint by 50 ms steps.
// Always thermodynamic diagram in UI matches this metric. 
Endpoint_histogram = from(Endpoint.latency).histogram(50)

// Caculate the percent of response status is true, for each service.
Endpoint_success = from(Endpoint.*).filter(status = "true").percent()

// Caculate the percent of response code in [200, 299], for each service.
Endpoint_200 = from(Endpoint.*).filter(responseCode like "2%").percent()

// Caculate the percent of response code in [500, 599], for each service.
Endpoint_500 = from(Endpoint.*).filter(responseCode like "5%").percent()

// Caculate the sum of calls for each service.
EndpointCalls = from(Endpoint.*).sum()
```