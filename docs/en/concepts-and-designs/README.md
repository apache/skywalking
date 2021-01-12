# Concepts and Designs
Concepts and Designs help you to learn and understand the SkyWalking and the landscape.

- What is SkyWalking?
  - [Overview and Core concepts](overview.md). Provides a high-level description and introduction, including the problems the project solves.
  - [Project Goals](project-goals.md). Provides the goals, which SkyWalking is trying to focus and provide features about them.

After you read the above documents, you should understand the SkyWalking basic goals. Now, you can choose which following parts 
you are interested, then dive in.   

- Probe
  - [Introduction](probe-introduction.md). Lead readers to understand what the probe is, how many different probes existed and 
why need them.
  - [Service auto instrument agent](service-agent.md). Introduce what the auto instrument agents do and which languages does
SkyWalking already support. 
  - [Manual instrument SDK](manual-sdk.md). Introduce the role of the manual instrument SDKs in SkyWalking ecosystem.
  - [Service Mesh probe](service-mesh-probe.md). Introduce why and how SkyWalking receive telemetry data from Service mesh and proxy probe.
- Backend
  - [Overview](backend-overview.md). Provides a high level introduction about the OAP backend.
  - [Observability Analysis Language](oal.md). Introduces the core languages, which is designed for aggregation behaviour definition.
  - [Query in OAP](../protocols/README.md#query-protocol). A set of query protocol provided, based on the Observability Analysis Language metrics definition. 
- UI
  - [Overview](ui-overview.md). A simple brief about SkyWalking UI.
- CLI
  - [SkyWalking CLI](https://github.com/apache/skywalking-cli). A command line interface for SkyWalking.
