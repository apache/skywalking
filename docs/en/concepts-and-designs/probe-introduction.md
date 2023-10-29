# Probe Introduction

In SkyWalking, probe means an agent or SDK library integrated into a target system that takes charge of collecting
telemetry data, including tracing and metrics. Depending on the target system tech stack, there are very different ways
how the probe performs such tasks. But ultimately, they all work towards the same goal — to collect and reformat data,
and then to send them to the backend.

On a high level, there are four typical categories in all SkyWalking probes.

- **Language based native agent**. These agents run in target service user spaces, such as a part of user codes. For
  example, the SkyWalking Java agent uses the `-javaagent` command line argument to manipulate codes in runtime,
  where `manipulate` means to change and inject user's codes. Another example is SkyWalking agent, which leverage Golang
  compiling mechanism to weaves codes in the compiling time. For some static compilation languages, such as C++, manual
  library is the only choice.
  As you can see, these agents are based on languages and libraries, no matter we provide auto instrument or manual agents.

- **Service Mesh probes**. Service Mesh probes collect data from sidecar, control plane in service mesh or proxy. In the
  old days, proxy is only used as an ingress of the whole cluster, but with the Service Mesh and sidecar, we can now
  perform observability functions.

- **3rd-party instrument library**. SkyWalking accepts many widely used instrument libraries data formats. 
  SkyWalking community is connected closely with Zipkin community, it could work as an alternative server for both v1 and
  v2 Zipkin traces. Also, OTEL trace format in gRPC is supported, and converted to Zipkin format inside SkyWalking.
  As an alternative Zipkin server, Zipkin lens UI could be used to visualize accepted traces when they are in Zipkin format.
  See [Receiver for Zipkin traces](../setup/backend/zipkin-trace.md) and [Receiver for OTEL traces](../setup/backend/otlp-trace.md) for more information.

- **eBPF agent**. The eBPF agent collects metrics and profiling the target service powered by the eBPF technology of Linux kernel.

You don't have to install all probes to make SkyWalking up and running. 
There are several recommended ways on how to use these probes:

1. Use **Language based native agent** only to build topology and metrics for your business application.
1. Use **3rd-party instrument library** only, like the Zipkin instrument ecosystem.
1. Use **Service Mesh probe** if you prefer Service Mesh stack and don't want to use native agents.
1. Use **Service Mesh probe** with **Language based native agent** or **3rd-party instrument library** in pure tracing
   status. (Advanced usage)
1. Use **eBPF agent** only if you only want to profile on demand and/or activating automatic performance analysis.
1. Use **eBPF agent** with **Language based native agent** collaboratively. Enhance the traces with the eBPF agent to collect extra information.

What is the meaning of **in tracing status**?

By default, **Language based native agent** and **3rd-party instrument library** both send distributed traces to the
backend, where analyses and aggregation on those traces are performed. **In pure tracing status** means that the backend
considers these traces as something like logs. In other words, the backend saves them, but doesn't run the metrics analysis from
traces. As a result, there would not have data of `service/instance/endpoint metrics and relationships`.

## What is next?

- Learn more about the probes supported by SkyWalking in [Service auto instrument agent](service-agent.md)
  , [Manual instrument SDK](manual-sdk.md) and [Zipkin receiver](../setup/backend/zipkin-trace.md).
- After understanding how the probe works, see the [backend overview](backend-overview.md) for more on analysis and
  persistence.

