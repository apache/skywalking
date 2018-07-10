# Observability Analysis Platform Cluster Management
OAP(Observability Analysis Platform) server is a distributed system, services need to find each 
other. i.e. a web service needs to find query service, level 1 aggregate service need to find 
level 2 aggregate service. Cluster management is just a client-side implementation. It must work 
together with a distributed coordination server, (i.e. Zookeeper, Consul, Kubernetes.) unless 
running in standalone mode.

## Architecture overview
<img src="https://skywalkingtest.github.io/page-resources/6.0.0-alpha/cluster_management.png"/>

### Cluster Management Plugins
By default, OAP server provides two implementations for cluster management, which are standalone 
and zookeeper. When the applications being monitored are small, you can choose the standalone mode.
If they are big, you must choose the cluster mode by zookeeper plugin, or you can implement another 
service discovery plugin for your own scene.

### Cluster Management Interface
There are two interfaces defined beforehand in the OAP server core, which are Module register and 
module query, all the cluster management plugins must implement those two interfaces.

* Module Register: When any modules which need to provide services for each other, those modules 
must invoke this interface to register itself into the service discovery server.
* Module Query: When any modules which need to call each other, those modules must retrieve the 
module set by this interface.

### Process Flow Between Client and Cluster Management
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