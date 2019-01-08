# Cluster Management
In many product environments, backend need to support high throughputs and provide HA to keep robustness,
so you should need cluster management always in product env.
 
Backend provides several ways to do cluster management. Choose the one you need/want.

- [Zookeeper coordinator](#zookeeper-coordinator). Use Zookeeper to let backend detects and communicates
with each other.
- [Kubernetes](#kubernetes). When backend cluster are deployed inside kubernetes, you could choose this
by using k8s native APIs to manage cluster.


## Zookeeper coordinator
Zookeeper is a very common and wide used cluster coordinator. Set the **cluster** module's implementor
to **zookeeper** in the yml to active. 

Required Zookeeper version, 3.4+

```yaml
cluster:
  zookeeper:
    nameSpace: ${SW_NAMESPACE:""}
    hostPort: ${SW_CLUSTER_ZK_HOST_PORT:localhost:2181}
    # Retry Policy
    baseSleepTimeMs: 1000 # initial amount of time to wait between retries
    maxRetries: 3 # max number of times to retry
```

- `hostPort` is the list of zookeeper servers. Format is `IP1:PORT1,IP2:PORT2,...,IPn:PORTn`
- `hostPort`, `baseSleepTimeMs` and `maxRetries` are settings of Zookeeper curator client.


## Kubernetes
Require backend cluster are deployed inside kubernetes, guides are in [Deploy in kubernetes](backend-k8s.md).
Set implementor to `kubernetes`.

```yaml
cluster:
  kubernetes:
    watchTimeoutSeconds: 60
    namespace: default
    labelSelector: app=collector,release=skywalking
    uidEnvName: SKYWALKING_COLLECTOR_UID
```

## Consul
Now, consul is becoming a famous system, many of companies and developers using consul to be 
their service discovery solution. Set the **cluster** module's implementor to **consul** in 
the yml to active. 

```yaml
cluster:
  consul:
    serviceName: ${SW_SERVICE_NAME:"SkyWalking_OAP_Cluster"}
    # Consul cluster nodes, example: 10.0.0.1:8500,10.0.0.2:8500,10.0.0.3:8500
    hostPort: ${SW_CLUSTER_CONSUL_HOST_PORT:localhost:8500}
```