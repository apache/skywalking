# Overview
SkyWalking: an open source observability platform to collect, analysis, aggregate and visualize data from services and cloud native 
infrastructures. SkyWalking provide an easy way to keep you have clear view about your distributed system, even across Cloud.
It is more like an modern APM, especially designed for cloud native, container based and distributed system.

## Why use SkyWalking?
SkyWalking provides solutions for observing and monitoring distributed system, in many different scenarios. First of all,
like traditional ways, SkyWalking provides auto instrument agents for service, such as Java, C#
and NodeJS. At same time, provide manual instrument SDKs for Go(Not yet), C++(Not yet). 
Also with more languages required, risks in manipulating codes at runtime, cloud native infrastructures grow 
more powerful, SkyWalking could use Service Mesher infra probes to collect data for understanding the whole distributed system.
In generally, it provides observability capabilities for **service**(s), **service instance**(s), **endpoint**(s).

Service, instance and endpoint concepts are using everywhere in today, so let's be clear what we mean these in SkyWalking first.

- **Service**. Represent a set/group of workloads to provide same behaviours for incoming requests. You can define the service 
name when you are using instrument agents or SDKs. Or SkyWalking use the name you defined in platform, such as from Istio.
- **Service Instance**. Each one workload in the Service group, named as instance. Like `pods` in Kubernetes, this does't need 
to be a single process in OS. Also if you are using instrument agents, instance is actually a real process in OS.
- **Endpoint**. It is a path in the certain service for incoming requests, such as HTTP URI path or gRPC service class + method
signature. 

By using SkyWalking, you can have the topology relationship between Services and Endpoints, metrics of every Service, Service
Instance and Endpoint and set alarm rules.

In addition to all there, you can have distributed tracing from SkyWalking native agents and SDKs, also by using Zipkin, Jaeger
and OpenCensus.

## Architecture
The SkyWalking is logically split into four parts: Probes, Platform backend, Storage, UI.

<img src="https://skywalkingtest.github.io/page-resources/6_overview.png"/>

- The **Probe**s are different based on different sources. They collects data and reformat them in SkyWalking requirements.
- THe **Platform backend** is a backend, supported cluster for sure. It is for aggregation, analysis and driving process flow
from probe to UI. Also it provide the pluggable capabilities for incoming formats(like Zipkin's), storage implementors and cluster
management. You even can customize aggregation and analysis by using [Observability Analysis Language](oal.md).
- The **Storage** is open. You can, either choose an existed implementor, such as ElasticSearch, H2 or MySQL cluster managed by 
Sharding-Sphere, or implement your own. Of source, we are very welcome to have your contributions for new storage implementors.
- THe **UI** is cool and very powerful for end user of SkyWalking platform. UI is customizable too, because 
you need match the backend if you do customize too.


## What is next?
- Learn SkyWalking's [Project Goals](project-goals.md)