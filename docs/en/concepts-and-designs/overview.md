# Overview
SkyWalking is an open source observability platform used to collect, analyze, aggregate and visualize data from services and cloud native
infrastructures. SkyWalking provides an easy way to maintain a clear view of your distributed systems, even across Clouds.
It is a modern APM, specially designed for cloud native, container based distributed systems.

## Why use SkyWalking?
SkyWalking provides solutions for observing and monitoring distributed systems, in many different scenarios. First of all,
like traditional approaches, SkyWalking provides auto instrument agents for services, such as Java, C#, Node.js, Go, PHP and Nginx LUA. 
(with calls out for Python and C++ SDK contributions). 
In multi-language, continuously deployed environments, cloud native infrastructures grow more powerful but also more complex. 
SkyWalking's service mesh receiver allows SkyWalking to receive telemetry data from service mesh frameworks
such as Istio/Envoy and Linkerd, allowing users to understand the entire distributed system.

SkyWalking provides observability capabilities for **service**(s), **service instance**(s), **endpoint**(s), **process**(s). The terms Service,
Instance and Endpoint are used everywhere today, so it is worth defining their specific meanings in the context of SkyWalking:

- **Service**. Represents a set/group of workloads which provide the same behaviours for incoming requests. You can define the service
  name when you are using instrument agents or SDKs. SkyWalking can also use the name you define in platforms such as Istio.
- **Service Instance**. Each individual workload in the Service group is known as an instance. Like `pods` in Kubernetes, it 
  doesn't need to be a single OS process, however, if you are using instrument agents, an instance is actually a real OS process.
- **Endpoint**. A path in a service for incoming requests, such as an HTTP URI path or a gRPC service class + method signature. 
- **Process**. An operating system process. In some scenarios, a Service Instance is
  not a process, such as a pod Kubernetes could contain multiple processes.

SkyWalking allows users to understand the topology relationship between Services and Endpoints, to view the metrics of every 
Service/Service Instance/Endpoint and to set alarm rules.

Starting from v9, SkyWalking introduces the new core concept **Layer**.
A **layer** represents an abstract framework in computer science, such as Operating System(OS_LINUX layer),
Kubernetes(k8s layer). All detected instances belong to a layer to represent the running environment of this instance, 
the service would have one or multiple layer definitions according to its instances.

In addition, you can integrate 
1. Other distributed tracing using Zipkin.
1. Other metrics systems, such as Prometheus, Sleuth(Micrometer), OpenTelemetry, Telegraf.


## Architecture
SkyWalking is logically split into four parts: Probes, Platform backend, Storage and UI.

<img src="https://skywalking.apache.org/images/home/architecture_2160x720.png?t=20220617"/>

- **Probe**s collect telemetry data, including metrics, traces, logs and events in various formats(SkyWalking, Zipkin, OpenTelemetry, Prometheus, Zabbix, etc.)
- **Platform backend** supports data aggregation, analysis and streaming process covers traces, metrics, logs and events. Work as Aggregator Role, Receiver Role or both.
- **Storage** houses SkyWalking data through an open/plugable interface. You can choose an existing implementation, such as
  ElasticSearch, H2, MySQL, TiDB, BanyanDB, or implement your own. 
- **UI** is a highly customizable web based interface allowing SkyWalking end users to visualize and manage SkyWalking data.


## What is next?
- Learn SkyWalking's [Project Goals](project-goals.md)
- FAQ, [Why doesn't SkyWalking involve MQ in the architecture in default?](../FAQ/why_mq_not_involved.md)
