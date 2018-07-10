# Protocols
There are two types of protocols list here. 

- **Probe Protocol**. Include the descriptions and defines about how agent send collected metric data and traces, also the formats of each entities.

- **Query Protocol**. The backend provide query capability to SkyWalking own UI and others. These queries are based on GraphQL.

## Probe Protocol
They also related to the probe group, for understand that, look [Concepts and Designs](../concepts-and-designs/README.md) document.
These groups are **Language based native agent protocol**, **Service Mesh protocol** and **3rd-party instrument protocol**.  


## Language based native agent protocol
This protocol is combined from two parts:
* [Cross Process Propagation Headers Protocol](Skywalking-Cross-Process-Propagation-Headers-Protocol-v1.md) is for in-wire propagation.
which is usually used to carrier the necessary info to build trace.
* [SkyWalking Trace Data Protocol](Trace-Data-Protocol.md) define the communication way and format between agent and backend.


## Service Mesh probe protocol