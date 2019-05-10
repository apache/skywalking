# Design Goals
The document outlines the core design goals for SkyWalking project.

- **Keep Observability**. No matter how does the target system deploy, SkyWalking could provide a solution or 
integration way to keep observability for it. Based on this, SkyWalking provides several runtime forms and probes.

- **Topology, Metrics and Trace Together**. The first step to see and understand a distributed system should be 
from topology map. It visualizes the whole complex system as an easy map. Under that topology, OSS people requires
more about metrics for service, instance, endpoint and calls. Trace exists as detail logs for making sense of those metrics.
Such as when endpoint latency becomes long, you want to see the slowest the trace to find out why. So you can see,
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
SkyWalking should run well in all these cases.

- **Interop**. Observability is a big landscape, SkyWalking is impossible to support all, even by its community.
As that, it supports to interop with other OSS system, mostly probes, such as Zipkin, Jaeger, OpenTracing, OpenCensus.
To accept and understand their data formats makes sure SkyWalking more useful for end users. And don't require
the users to switch their libraries.


## What is next?
- See [probe Introduction](probe-introduction.md) to know SkyWalking's probe groups.
- From [backend overview](backend-overview.md), you can understand what backend does after it received probe data.
- If you want to customize UI, start with [UI overview](ui-overview.md) document. 