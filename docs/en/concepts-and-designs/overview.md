# Overview
SkyWalking: an open source observability platform to collect, analyze, aggregate and visualize data from services and cloud native
infrastructures. SkyWalking provides an easy way to keep you have a clear view of your distributed system, even across Cloud.
It is more like a modern APM, specially designed for cloud native, container based and distributed system.

## Why use SkyWalking?
SkyWalking provides solutions for observing and monitoring distributed system, in many different scenarios. First of all,
like traditional ways, SkyWalking provides auto instrument agents for service, such as Java, C#
and Node.js. At the same time, it provides manual instrument SDKs for Go(Not yet), C++(Not yet).
Also with more languages required, risks in manipulating codes at runtime, cloud native infrastructures grow 
more powerful, SkyWalking could use Service Mesher infra probes to collect data for understanding the whole distributed system.
In general, it provides observability capabilities for **service**(s), **service instance**(s), **endpoint**(s).

The concepts of Service, Instance and Endpoint are used everywhere today, so let's make it clear about what they mean in SkyWalking first.

- **Service**. Represent a set/group of workloads to provide the same behaviours for incoming requests. You can define the service
  name when you are using instrument agents or SDKs. Or SkyWalking uses the name you defined in platform such as Istio.
- **Service Instance**. Each one workload in the Service group is named as an instance. Like `pods` in Kubernetes, it doesn't need
  to be a single process in OS. Also if you are using instrument agents, an instance is actually a real process in OS.
- **Endpoint**. It is a path in the certain service for incoming requests, such as HTTP URI path or gRPC service class + method
signature. 

By using SkyWalking, you can have the topology relationship between Services and Endpoints, metrics of every Service, Service
Instance and Endpoint and set alarm rules.

In addition to all these, you can have distributed tracing from SkyWalking native agents and SDKs, also by using Zipkin, Jaeger
and OpenCensus.

## Architecture
The SkyWalking is logically split into four parts: Probes, Platform backend, Storage and UI.

<img src="http://skywalking.apache.org/assets/frame.jpeg"/>

- The **Probe**s are different based on different sources. They collect data and reformat them in SkyWalking requirements.
- The **Platform backend** is a backend, supported cluster for sure. It is for aggregation, analysis and driving process flow
from probe to UI. It also provides the pluggable capabilities for incoming formats(like Zipkin's), storage implementors and cluster
 management. You even can customize aggregation and analysis by using [Observability Analysis Language](oal.md).
- The **Storage** is open. You can, either choose an existed implementor, such as ElasticSearch, H2 or MySQL cluster managed by 
Sharding-Sphere, or implement your own. Of course, we are very appreciated to have your contributions for new storage implementors.
- The **UI** is cool and very powerful for SkyWalking end users. It is also customizable to match your customized backend.


## What is next?
- Learn SkyWalking's [Project Goals](project-goals.md)
- FAQ, [Why doesn't SkyWalking involve MQ in the architecture?](../FAQ/why_mq_not_involved.md)
