# Protocols
There are two types of protocols list here. 

- [**Probe Protocol**](#probe-protocols). Include the descriptions and definitions about how agent send collected metric data and traces, also the formats of each entities.

- [**Query Protocol**](#query-protocol). The backend provide query capability to SkyWalking own UI and others. These queries are based on GraphQL.


## Probe Protocols
They also related to the probe group, for understand that, look [Concepts and Designs](../concepts-and-designs/README.md) document.
These groups are **Language based native agent protocol**, **Service Mesh protocol** and **3rd-party instrument protocol**.

## Register Protocol
Include service, service instance, network address and endpoint meta data register.
Purposes of register are
1. For service, network address and endpoint, register returns the unique ID of register object, usually an integer. Probe
can use that to represent the literal String for data compression. Further, some protocols accept IDs only.
1. For service instance, register returns a new unique ID for every new instance. Every service instance register must contain the 
service ID.
 


### Language based native agent protocol
There is two types of protocols to make language agents work in distributed environments.
1. **Cross Process Propagation Headers Protocol** is in wire data format, agent/SDK usually uses HTTP/MQ/HTTP2 headers
to carry the data with rpc request. The remote agent will receive this in the request handler, and bind the context 
with this specific request.
1. **Trace Data Protocol** is out of wire data, agent/SDK uses this to send traces and metrics to skywalking or other
compatible backend. 

Header protocol have two formats for compatible. Using v2 in default.
* [Cross Process Propagation Headers Protocol v2](Skywalking-Cross-Process-Propagation-Headers-Protocol-v2.md) is the new protocol for 
in-wire context propagation, started in 6.0.0-beta release. It will replace the old **SW3** protocol in the future, now both of them are supported.
* [Cross Process Propagation Headers Protocol v1](Skywalking-Cross-Process-Propagation-Headers-Protocol-v1.md) is for in-wire propagation.
By following this protocol, the trace segments in different processes could be linked.

Since SkyWalking v6.0.0-beta, SkyWalking agent and backend are using Trace Data Protocol v2, and v1 is still supported in backend.
* [SkyWalking Trace Data Protocol v2](Trace-Data-Protocol-v2.md) define the communication way and format between agent and backend
* [SkyWalking Trace Data Protocol v1](Trace-Data-Protocol.md). This protocol is used in old version. Still supported.


### Service Mesh probe protocol
The probe in sidecar or proxy could use this protocol to send data to backendEnd. This service provided by gRPC, requires 
the following key info:

1. Service Name or ID at both sides.
1. Service Instance Name or ID at both sides.
1. Endpoint. URI in HTTP, service method full signature in gRPC.
1. Latency. In milliseconds.
1. Response code in HTTP
1. Status. Success or fail.
1. Protocol. HTTP, gRPC
1. DetectPoint. In Service Mesh sidecar, `client` or `server`. In normal L7 proxy, value is `proxy`.


### 3rd-party instrument protocol
3rd-party instrument protocols are not defined by SkyWalking. They are just protocols/formats, which SkyWalking is compatible and
could receive from their existed libraries. SkyWalking starts with supporting Zipkin v1, v2 data formats.

Backend is based on modulization principle, so very easy to extend a new receiver to support new protocol/format.

## Query Protocol
Query protocol follows GraphQL grammar, provides data query capabilities, which depends on your analysis metrics.

There are 5 dimensionality data is provided.
1. Metadata. Metadata includes the brief info of the whole under monitoring services and their instances, endpoints, etc.
Use multiple ways to query this meta data.
1. Topology. Show the topology and dependency graph of services or endpoints. Including direct relationship or global map.
1. Metric. Metric query targets all the objects defined in [OAL script](../concepts-and-designs/oal.md). You could get the 
metric data in linear or thermodynamic matrix formats based on the aggregation functions in script. 
1. Aggregation. Aggregation query means the metric data need a secondary aggregation in query stage, which makes the query 
interfaces have some different arguments. Such as, `TopN` list of services is a very typical aggregation query, 
metric stream aggregation just calculates the metric values of each service, but the expected list needs ordering metric data
by the values.
1. Trace. Query distributed traces by this.
1. Alarm. Through alarm query, you can have alarm trend and details.

The actual query GraphQL scrips could be found inside `query-protocol` folder in [here](../../../oap-server/server-query-plugin/query-graphql-plugin/src/main/resources).

Here is the list of all existing metric names, based on [official_analysis.oal](../../../oap-server/generated-analysis/src/main/resources/official_analysis.oal)

**Global metric**
- all_p99, p99 response time of all services
- all_p95
- all_p90
- all_p75
- all_p70
- all_heatmap, the response time heatmap of all services 

**Service metric**
- service_resp_time, avg response time of service
- service_sla, successful rate of service
- service_cpm, calls per minute of service
- service_p99, p99 response time of service
- service_p95
- service_p90
- service_p75
- service_p50

**Service instance metric**
- service_instance_sla, successful rate of service instance
- service_instance_resp_time, avg response time of service instance
- service_instance_cpm, calls per minute of service instance

**Endpoint metric**
- endpoint_cpm, calls per minute of endpoint
- endpoint_avg, avg response time of endpoint
- endpoint_sla, successful rate of endpoint
- endpoint_p99, p99 response time of endpoint
- endpoint_p95
- endpoint_p90
- endpoint_p75
- endpoint_p50

**JVM metric**, JVM related metric, only work when javaagent is active
- instance_jvm_cpu
- instance_jvm_memory_heap
- instance_jvm_memory_noheap
- instance_jvm_memory_heap_max
- instance_jvm_memory_noheap_max
- instance_jvm_young_gc_time
- instance_jvm_old_gc_time
- instance_jvm_young_gc_count
- instance_jvm_old_gc_count

**Service relation metric**, represents the metric of calls between service. 
The metric ID could be
got in topology query only.
- service_relation_client_cpm, calls per minut detected at client side
- service_relation_server_cpm, calls per minut detected at server side
- service_relation_client_call_sla, successful rate detected at client side
- service_relation_server_call_sla, successful rate detected at server side
- service_relation_client_resp_time, avg response time detected at client side
- service_relation_server_resp_time, avg response time detected at client side

**Endpoint relation metric**, represents the metric between dependency endpoints. Only work when tracing agent.
The metric ID could be got in topology query only.
- endpoint_relation_cpm
- endpoint_relation_resp_time