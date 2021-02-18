# Probe Introduction
In SkyWalking, probe means an agent or SDK library integrated into target system, which take charge of 
collecting telemetry data including tracing and metrics. Based on the target system tech stack, probe could use very different
ways to do so. But ultimately they are same, just collect and reformat data, then send to backend.

In high level, there are three typical groups in all SkyWalking probes.
- **Language based native agent**. This kind of agents runs in target service user space, like a part of user codes. Such as
SkyWalking Java agent, use `-javaagent` command line argument to manipulate codes in runtime, `manipulate` means change and inject
user's codes. Another kind of agents is using some hook or intercept mechanism provided by target libraries. So you can see, these kinds
of agents based on languages and libraries.
 
- **Service Mesh probe**. Service Mesh probe collects data from sidecar, control panel in service mesh or proxy. In old days, proxy
is only used as ingress of the whole cluster, but with the Service Mesh and sidecar, now we can do observe based on that.
 
- **3rd-party instrument library**. SkyWalking accepts other popular used instrument libraries data format. It analysis the
data, transfer it to SkyWalking formats of trace, metrics or both. This feature starts with accepting Zipkin span data. See
[Receiver for other tracers](../setup/backend/backend-receivers.md) to know more. 

You don't need to use **Language based native agent** and **Service Mesh probe** at the same time, because they both collect
metrics data. As a result of that, your system suffers twice payloads, and the analytic numbers are doubled.

There are several recommend ways in using these probes:
1. Use **Language based native agent** only.
1. Use **3rd-party instrument library** only, like Zipkin instrument ecosystem.
1. Use **Service Mesh probe** only.
1. Use **Service Mesh probe** with **Language based native agent** or **3rd-party instrument library** in tracing status. (Advanced usage)

In addition, let's example what is the meaning of **in tracing status**?

In default, **Language based native agent** and **3rd-party instrument library** both send distributed traces to backend,
which do analysis and aggregate on those traces. **In tracing status** means, backend considers these traces as something
like logs, just save them, and build the links between traces and metrics, like `which endpoint and service does the trace belong?`.

## What is next?
- Learn the SkyWalking supported probes in [Service auto instrument agent](service-agent.md), [Manual instrument SDK](manual-sdk.md),
[Service Mesh probe](service-mesh-probe.md) and [Zipkin receiver](../setup/backend/backend-receivers.md#zipkin-receiver).
- After understand the probe, read [backend overview](backend-overview.md) for understanding analysis and persistence.

