# Protocols
There are two types of protocols list here. 

- [**Probe Protocol**](#probe-protocols). Include the descriptions and definitions about how agent send collected metrics data and traces, also the formats of each entities.

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

[Cross Process Propagation Headers Protocol v2](Skywalking-Cross-Process-Propagation-Headers-Protocol-v2.md) is the new protocol for 
in-wire context propagation, started in 6.0.0-beta release, older protocol is no longer supported.

Since SkyWalking v6.0.0-beta, SkyWalking agent and backend are using Trace Data Protocol v2.
[SkyWalking Trace Data Protocol v2](Trace-Data-Protocol-v2.md) define the communication way and format between agent and backend.


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

Backend is based on modularization principle, so very easy to extend a new receiver to support new protocol/format.

## Query Protocol
Query protocol follows GraphQL grammar, provides data query capabilities, which depends on your analysis metrics.
Read [query protocol doc](query-protocol.md) for more details.
