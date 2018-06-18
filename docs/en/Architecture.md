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
First of all, the collector provides two types of connections, also two protocols(HTTP and gRPC). These two are
1. Naming service in HTTP, which returns the all available collectors in the backend cluster.
1. Uplink service in gRPC(primary in SkyWalking native agents) and HTTP, which uplinks traces and metrics to collector.
Each client will only send monitoring data(traces and metrics) to a single collector. Attempt to connect other if the connected one offline.

Such as in SkyWalking Java agent
1. `collector.servers` means the naming service, which maps to `naming/jetty/ip:port` of collector, in HTTP. 
1. `collector.direct_servers` means setting Uplink service directly, and using gRPC to send monitoring data.


_Example of the process flow between client lib and collector cluster_
```
         Client lib                         Collector1             Collector2              Collector3
 (Set collector.servers=Collector2)              (Collector 1,2,3 constitute the cluster)
             |
             +-----------> naming service ---------------------------->|
                                                                       |
             |<------- receive gRPC IP:Port(s) of Collector 1,2,3---<--|
             |
             |Select a random gRPC service
             |For example collector 3
             |
             |------------------------->Uplink gRPC service----------------------------------->|
```


## Collector Cluster Discovery
When collectors are running in cluster mode, collector must discovery each other
