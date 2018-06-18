# Architecture Design
## Background
For APM, agent or SDKs are just about the technical details about how to instrument the libs. 
Manual or auto are nothing about the architecture, so in this document, we will consider them as a client lib only.

## Basic Principles
The basic design principles of SkyWalking architecture are **easy to maintain, controllable and stream process module**. 

In order to achieve these goals, SkyWalking backend provides the following designs.
1. Modulization design.
1. Multiple connection ways for clients sides.
1. Collector cluster discovery mechanism
1. Streaming mode.
1. Swtichable storage implementors.

## Modulization
SkyWalking collector is based on pure **modulization design**. End user can switch or assemble the collector features by their
own requirements.

### Module

Module defines a collection of features, which could include techenical implementors(such as: gRPC/Jetty server managements), 
trace analysis(such as: trace segment or zipkin span parser), or aggregation feature. Totally decided by the module definition
and its implementors.

Each module could define their services in Java Interface, 
and every providers of the module must provide implementors for these services. 
And the provider should define the dependency modules based its own implementation. 
So it means, even two different implementors of the module, could depend different modules.

Also the collector modulization core checks the startup sequences, if cycle dependency or dependency not found occurs, 
collector should be terminated by core.

The collector startup all modules, which are decleared in `application.yml`. 
In this yaml file
- Root level is the module name, such as `cluster`, `naming`
- Secondary level is the implementor name of the module, such as `zookeeper` is the `cluster` module
- Third level are attributes of the implementors. Such as `hostPort` and `sessionTimeout` are required attributes of `zookepper`.

_The example part of the yaml definitation_
```yml
cluster:
  zookeeper:
    hostPort: localhost:2181
    sessionTimeout: 100000
naming:
  jetty:
    #OS real network IP(binding required), for agent to find collector cluster
    host: localhost
    port: 10800
    contextPath: /
```

## Multiple connection ways
There are two major connection ways from client to collectors.
