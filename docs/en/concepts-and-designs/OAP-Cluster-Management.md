# Observability Analysis Platform Cluster Management
OAP(Observability Analysis Platform) backend is a distributed system, services need to find each 
other. i.e. a web service needs to find query service, level 1 aggregate service need to find 
level 2 aggregate service. Cluster management is just a client-side implementation. It must work 
together with a distributed coordination service, (i.e. Zookeeper, Consul, Kubernetes.) unless 
running in standalone mode.

## Architecture overview
<img src="https://skywalkingtest.github.io/page-resources/6.0.0-alpha/cluster_management.png"/>

### Cluster Management Plugins
By default, OAP backend provides two implementations for cluster management, which are standalone 
and zookeeper. When the scale of services, which are under monitoring, is small, you can choose 
the standalone mode. Otherwise, you must choose the cluster mode by zookeeper plugin, or you can 
implement a new service discovery plugin for your own scene.

### Cluster Management Interface
There are two interfaces defined beforehand in the OAP server core, which are Module register and 
module query, all the cluster management plugins must implement those two interfaces.

* Module Register: When any modules need to provide services for others, the module must do register 
through this interface.
* Module Query: When any module needs to find other services, it can use this interface to retrieve 
the service instance list in the certain order.

### Process Flow Between Client and Cluster Management
The client has two ways to connect the backend, one, use the direct link by a set of backend instance 
endpoint list, or you can use naming service, which considers your given list is just the seed nodes 
of the whole cluster. The following graph is showing you how naming service works. You need to check 
the probe documents to know which way(s) is(are) supported.
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