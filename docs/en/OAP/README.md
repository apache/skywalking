# Observability Analysis Platform
OAP(Observability Analysis Platform) is a new concept, which starts in SkyWalking 6.x. OAP replaces the 
old SkyWalking whole backend. The capabilities of the platform are following.

## OAP capabilities
<img src="https://skywalkingtest.github.io/page-resources/6_overview.png"/>

In SkyWalking 6 series, OAP accepts data from more sources, which belongs two groups: **Tracing** and **Metric**.

- **Tracing**. Including, SkyWalking native data formats. Zipkin v1,v2 data formats and Jaeger data formats.
- **Metric**. SkyWalking integrates with Service Mesh platforms, such as Istio, Envoy, Linkerd, to provide observability from data panel 
or control panel. Also, SkyWalking native agents can run in metric mode, which highly improve the 
performance.

At the same time by using any integration solution provided, such as SkyWalking log plugin or toolkits, 
SkyWalking provides visualization integration for binding tracing and logging together by using the 
trace id and span id.

As usual, all services provided by gRPC and HTTP protocol to make integration easier for unsupported ecosystem.

## Tracing in OAP
Tracing in OAP has two ways to process.
1. Traditional way in SkyWalking 5 series. Format tracing data in SkyWalking trace segment and span formats, 
even for Zipkin data format. The AOP analysis the segments to get metrics, and push the metric data into
the streaming aggregation.
1. Consider tracing as some kinds of logging only. Just provide save and visualization capabilities for trace. 

## Metric in OAP
Metric in OAP is totally new feature in 6 series. Build observability for a distributed system based on metric of connected nodes.
No tracing data is required.

Metric data are aggregated inside AOP cluster in streaming mode. See below about [Observability Analysis Language](#observability-analysis-language),
which provides the easy way to do aggregation and analysis in script style. 

### Observability Analysis Language
Provide OAL(Observability Analysis Language) to analysis incoming data in streaming mode. 

OAL focuses on metric in Service, Service Instance and Endpoint. Because of that, the language is easy to 
learn and use.

Considering performance, reading and debugging, OAL is defined as a compile language. 
The OAL scrips will be compiled to normal Java codes in package stage.

#### Grammar
Scripts should be named as `*.oal`
```

METRIC_NAME = from(SCOPE.(* | [FIELD][,FIELD ...]))
[.filter(FIELD OP [INT | STRING])]
.FUNCTION([PARAM][, PARAM ...])
```

#### Scope
**SCOPE** in (`All`, `Service`, `ServiceInstance`, `Endpoint`, `ServiceRelation`, `ServiceInstanceRelation`, `EndpointRelation`).

#### Field
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

#### Filter
Use filter to build the conditions for the value of fields, by using field name and expression.

#### Aggregation Function
The default functions are provided by SkyWalking OAP core, and could implement more.

Provided functions
- `avg`
- `p99`
- `p90`
- `p75`
- `p50`
- `percent`
- `histogram`
- `sum`

#### Metric name
The metric name for storage implementor, alarm and query modules. The type inference supported by core.

#### Group
All metric data will be grouped by Scope.ID and min-level TimeBucket. 

- In `Endpoint` scope, the Scope.ID = Endpoint id (the unique id based on service and its Endpoint)

#### Examples
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

## Query in OAP
Query is the core feature of OAP for visualization and other higher system. The query matches the metric type.

There are two types of query provided.
1. Hard codes query implementor
1. Metric style query of implementor

### Hard codes
Hard codes query implementor, is for complex logic query, such as: topology map, dependency map, which 
most likely relate to mapping mechanism of the node relationship.

Even so, hard codes implementors are based on metric style query too, just need extra codes to assemble the 
results.

### Metric style query
Metric style query is based on the given scope and metric name in oal scripts.

Metric style query provided in two ways
- GraphQL way. UI uses this directly, and assembles the pages.
- API way. Most for `Hard codes query implementor` to do extra works.

#### Grammar
```
Metric.Scope(SCOPE).Func(METRIC_NAME [, PARAM ...])
```

#### Scope
**SCOPE** in (`All`, `Service`, `ServiceInst`, `Endpoint`, `ServiceRelation`, `ServiceInstRelation`, `EndpointRelation`).

#### Metric name
Metric name is defined in oal script. Such as **EndpointCalls** is the name defined by `EndpointCalls = from(Endpoint.*).sum()`.

#### Metric Query Function
Metric Query Functions match the Aggregation Function in most cases, but include some order or filter features.
Try to keep the name as same as the aggregation functions.

Provided functions
- `top`
- `trend`
- `histogram`
- `sum`

#### Example
For `avg` aggregate func, `top` match it, also with parameter[1] of result size and parameter[2] of order
```
# for Service_avg = from(Service.latency).avg()
Metric.Scope("Service").topn("Service_avg", 10, "desc")
```

## Project structure overview
This overview shows maven modules AOP provided.
```
- SkyWalking Project
    - apm-commons
    - ...
    - apm-oap
        - oap-receiver
            - receiver-skywalking
            - receiver-zipkin
            - ...
        - oap-discovery
            - discovery-naming
            - discovery-zookeeper
            - discovery-standalone
            - ...
        - oap-register
            - register-skywalking
            - ...
        - oap-analysis
            - analysis-trace
            - analysis-metric
            - analysis-log
        - oap-web
        - oap-libs
            - cache-lib
            - remote-lib
            - storage-lib
            - client-lib
            - server-lib
 ```
