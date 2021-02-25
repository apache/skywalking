# Observability Analysis Platform
We define SkyWalking as an Observability Analysis Platform, which provides a full observability to the services running
either in brown zone, or green zone, even hybrid.

## Capabilities
SkyWalking covers all 3 fields of observability, including, **Tracing**, **Metrics** and **logging**.

- **Tracing**. Including, SkyWalking native data formats. Zipkin v1,v2 data formats and Jaeger data formats.
- **Metrics**. SkyWalking integrates with Service Mesh platforms, such as Istio, Envoy, Linkerd, to provide observability from data panel 
or control panel. Also, SkyWalking native agents can run in metrics mode, which highly improve the performance.
- **Logging**. Including the logs collected from disk or through network. Native agent could bind the tracing context with logs automatically,
or use SkyWalking to bind the trace and log through the text content.

There are 3 powerful and native language engines to focus on analyzing observability data from above fields.
1. [Observability Analysis Language](oal.md) processes the native traces and service mesh data.
1. [Meter Analysis Language](mal.md) does metrics calculation for native meter data, and adopts stable and widely used metrics system, such as Prometheus and OpenTelemetry.
1. [Log Analysis Language](lal.md) focuses on log contents and collaborate with Meter Analysis Language.