# Server Mesh Probe
Service Mesh probes use the extendable mechanism provided in Service Mesh implementor, like Istio.

## What is Service Mesh?
The following explanation came from Istio documents.
> The term service mesh is often used to describe the network of microservices that make up such applications and the interactions between them. 
As a service mesh grows in size and complexity, it can become harder to understand and manage. 
Its requirements can include discovery, load balancing, failure recovery, metrics, and monitoring, and often more complex operational requirements 
such as A/B testing, canary releases, rate limiting, access control, and end-to-end authentication.

## Where does the probe collect data from?
Istio is a very typical Service Mesh design and implementor. It defines **Control Panel** and **Data Panel**,
which are wide used. Here is Istio Architecture:

![Istio Architecture](https://istio.io/docs/ops/architecture/arch.svg)

Service Mesh probe can choose to collect data from **Control Panel** or **Data Panel**. In Istio, 
it means collecting telemetry data from Mixer(Control Panel) or Envoy sidecar(Data Panel). Underlying
they are same data, the probe collects two telemetry entities from client side and server side per request.

## How does Service Mesh make backend work?
From the probe, you can see there must have no trace related in this kind of probe, so why SkyWalking
platform still works?

Service Mesh probes collects telemetry data from each request, so it knows the source, destination, 
endpoint, latency and status. By those, backend can tell the whole topology map by combining these call 
as lines, and also the metrics of each nodes through their incoming request. Backend asked for the same
metrics data from parsing tracing data. So, the right expression is: 
**Service Mesh metrics are exact the metrics, what the traces parsers generate. They are same.**

## What is Next?
- If you want to use the service mesh probe, read [set SkyWalking on Service Mesh](../setup/README.md#on-service-mesh) document.
