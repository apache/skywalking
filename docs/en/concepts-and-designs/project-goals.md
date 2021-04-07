# Design Goals
This document outlines the core design goals for the SkyWalking project.

- **Maintaining Observability**. Regardless of the deployment method of the target system, SkyWalking provides an integration solution for it to maintain observability. Based on this, SkyWalking provides multiple runtime forms and probes.

- **Topology, Metrics and Trace Together**. The first step to understanding a distributed system is the topology map. It visualizes the entire complex system in an easy-to-read layout. Under the topology, the OSS personnel have higher requirements in terms of the metrics for service, instance, endpoint and calls. Traces are in the form of detailed logs to make sense of those metrics.
For example, when the endpoint latency becomes long, you want to see the slowest the trace to find out why. So you can see,
they are from big picture to details, they are all needed. SkyWalking integrates and provides a lot of features to
make this possible and easy understand.

- **Light Weight**. There two parts of light weight are needed. (1) In probe, we just depend on network
communication framework, prefer gRPC. By that, the probe should be as small as possible, to avoid the library
conflicts and the payload of VM, such as permsize requirement in JVM.
(2) As an observability platform, it is secondary and third level system in your project environment.
So we are using our own light weight framework to build the backend core. Then you don't need to 
deploy big data tech platform and maintain them. SkyWalking should be simple in tech stack.

- **Pluggable**. SkyWalking core team provides many default implementations, but definitely it is not enough,
and also don't fit every scenario. So, we provide a lot of features for being pluggable. 

- **Portability**.  SkyWalking can run in multiple environments, including: 
(1) Use traditional register center like eureka.
(2) Use RPC framework including service discovery, like Spring Cloud, Apache Dubbo.
(3) Use Service Mesh in modern infrastructure.
(4) Use cloud services.
(5) Across cloud deployment. 
SkyWalking should run well in all of these cases.

- **Interoperability**. The observability landscape is so vast that it is virtually impossible for SkyWalking to support all systems, even with the support of its community.
Currently, it supports interoperability with other OSS systems, especially probes, such as Zipkin, Jaeger, OpenTracing, and OpenCensus.
It is very important to end users that SkyWalking has the ability to accept and read these data formats, since the users are not required to switch their libraries.


## What is next?
- See [probe Introduction](probe-introduction.md) to learn about SkyWalking's probe groups.
- From [backend overview](backend-overview.md), you can understand what the backend does after it receives probe data.
- If you want to customize the UI, start with the [UI overview](ui-overview.md) document. 
