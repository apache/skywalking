# Probe Introduction
In SkyWalking, probe means an agent or SDK library integrated into a target system that takes charge of 
collecting telemetry data, including tracing and metrics. Depending on the target system tech stack, there are very different ways how the probe performs such tasks. But ultimately, they all work towards the same goal — to collect and reformat data, and then to send them to the backend.

On a high level, there are three typical categories in all SkyWalking probes.
- **Language based native agent**. These agents run in target service user spaces, such as a part of user codes. For example,
the SkyWalking Java agent uses the `-javaagent` command line argument to manipulate codes in runtime, where `manipulate` means to change and inject
user's codes. Another kind of agents uses certain hook or intercept mechanism provided by target libraries. As you can see, these agents are based on languages and libraries.
 
- **Service Mesh probes**. Service Mesh probes collect data from sidecar, control panel in service mesh or proxy. In the old days, proxy
is only used as an ingress of the whole cluster, but with the Service Mesh and sidecar, we can now perform observability functions.
 
- **3rd-party instrument library**. SkyWalking accepts many widely used instrument libraries data formats. It analyzes the
data, transfers it to SkyWalking's formats of trace, metrics or both. This feature starts with accepting Zipkin span data. See
[Receiver for other tracers](../setup/backend/backend-receivers.md) for more information. 

You don't need to use **Language based native agent** and **Service Mesh probe** at the same time, since they both serve to collect
metrics data. Otherwise, your system will suffer twice the payload, and the analytic numbers will be doubled.

There are several recommended ways on how to use these probes:
1. Use **Language based native agent** only.
1. Use **3rd-party instrument library** only, like the Zipkin instrument ecosystem.
1. Use **Service Mesh probe** only.
1. Use **Service Mesh probe** with **Language based native agent** or **3rd-party instrument library** in tracing status. (Advanced usage)

What is the meaning of **in tracing status**?

By default, **Language based native agent** and **3rd-party instrument library** both send distributed traces to the backend,
where analyses and aggregation on those traces are performed. **In tracing status** means that the backend considers these traces as something
like logs. In other words, the backend saves them, and builds the links between traces and metrics, like `which endpoint and service does the trace belong?`.

## What is next?
- Learn more about the probes supported by SkyWalking in [Service auto instrument agent](service-agent.md), [Manual instrument SDK](manual-sdk.md),
[Service Mesh probe](service-mesh-probe.md) and [Zipkin receiver](../setup/backend/backend-receivers.md#zipkin-receiver).
- After understanding how the probe works, see the [backend overview](backend-overview.md) for more on analysis and persistence.

