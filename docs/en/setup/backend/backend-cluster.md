# Cluster Management
In many product environments, the backend needs to support high throughput and provide HA to maintain robustness,
so you always need cluster management in product env.

NOTICE, cluster management doesn't provide service discovery mechanism for agents and probes. We recommend agents/probes using
gateway to load balancer to access OAP clusters.

The core feature of cluster management is supporting the whole OAP cluster running distributed aggregation and analysis for telemetry data.
 
There are various ways to manage the cluster in the backend. Choose the one that best suits your needs.

- [Zookeeper coordinator](#zookeeper-coordinator). Use Zookeeper to let the backend instances detect and communicate
with each other.
- [Kubernetes](#kubernetes). When the backend clusters are deployed inside Kubernetes, you could make use of this method
by using k8s native APIs to manage clusters.
- [Consul](#consul). Use Consul as the backend cluster management implementor and coordinate backend instances.
- [Etcd](#etcd). Use Etcd to coordinate backend instances.
- [Nacos](#nacos). Use Nacos to coordinate backend instances.
In the `application.yml` file, there are default configurations for the aforementioned coordinators under the section `cluster`.
You can specify any of them in the `selector` property to enable it.

## Zookeeper coordinator
Zookeeper is a very common and widely used cluster coordinator. Set the **cluster/selector** to **zookeeper** in the yml to enable it.

Required Zookeeper version: 3.5+

```yaml
cluster:
  selector: ${SW_CLUSTER:zookeeper}
  # other configurations
```

- `hostPort` is the list of zookeeper servers. Format is `IP1:PORT1,IP2:PORT2,...,IPn:PORTn`
- `enableACL` enable [Zookeeper ACL](https://zookeeper.apache.org/doc/r3.5.5/zookeeperProgrammers.html#sc_ZooKeeperAccessControl) to control access to its znode.
- `schema` is Zookeeper ACL schemas.
- `expression` is a expression of ACL. The format of the expression is specific to the [schema](https://zookeeper.apache.org/doc/r3.5.5/zookeeperProgrammers.html#sc_BuiltinACLSchemes). 
- `hostPort`, `baseSleepTimeMs` and `maxRetries` are settings of Zookeeper curator client.

Note: 
- If `Zookeeper ACL` is enabled and `/skywalking` exists, you must make sure that `SkyWalking` has `CREATE`, `READ` and `WRITE` permissions. If `/skywalking` does not exist, it will be created by SkyWalking and all permissions to the specified user will be granted. Simultaneously, znode grants the READ permission to anyone.
- If you set `schema` as `digest`, the password of the expression is set in **clear text**. 

In some cases, the OAP default gRPC host and port in core are not suitable for internal communication among the OAP nodes.
The following settings are provided to set the host and port manually, based on your own LAN env.
- internalComHost: The registered host and other OAP nodes use this to communicate with the current node.
- internalComPort: the registered port and other OAP nodes use this to communicate with the current node.

```yaml
zookeeper:
  nameSpace: ${SW_NAMESPACE:""}
  hostPort: ${SW_CLUSTER_ZK_HOST_PORT:localhost:2181}
  #Retry Policy
  baseSleepTimeMs: ${SW_CLUSTER_ZK_SLEEP_TIME:1000} # initial amount of time to wait between retries
  maxRetries: ${SW_CLUSTER_ZK_MAX_RETRIES:3} # max number of times to retry
  internalComHost: 172.10.4.10
  internalComPort: 11800
  # Enable ACL
  enableACL: ${SW_ZK_ENABLE_ACL:false} # disable ACL in default
  schema: ${SW_ZK_SCHEMA:digest} # only support digest schema
  expression: ${SW_ZK_EXPRESSION:skywalking:skywalking}
``` 


## Kubernetes
The require backend clusters are deployed inside Kubernetes. See the guides in [Deploy in kubernetes](backend-k8s.md).
Set the selector to `kubernetes`.

```yaml
cluster:
  selector: ${SW_CLUSTER:kubernetes}
  # other configurations
```

## Consul
Recently, the Consul system has become more and more popular, and many companies and developers now use Consul as 
their service discovery solution. Set the **cluster/selector** to **consul** in the yml to enable it.

```yaml
cluster:
  selector: ${SW_CLUSTER:consul}
  # other configurations
```

Same as the Zookeeper coordinator,
in some cases, the OAP default gRPC host and port in core are not suitable for internal communication among the OAP nodes.
The following settings are provided to set the host and port manually, based on your own LAN env.
- internalComHost: The registed host and other OAP nodes use this to communicate with the current node.
- internalComPort: The registered port and other OAP nodes use this to communicate with the current node.


## Etcd
Set the **cluster/selector** to **etcd** in the yml to enable it. The Etcd client has upgraded to v3 protocol and changed to the CoreOS official library. **Since 8.7.0, only the v3 protocol is supported for Etcd.** 

```yaml
cluster:
  selector: ${SW_CLUSTER:etcd}
  # other configurations
  etcd:
    # etcd cluster nodes, example: 10.0.0.1:2379,10.0.0.2:2379,10.0.0.3:2379
    endpoints: ${SW_CLUSTER_ETCD_ENDPOINTS:localhost:2379}
    namespace: ${SW_CLUSTER_ETCD_NAMESPACE:/skywalking}
    serviceName: ${SW_SCLUSTER_ETCD_ERVICE_NAME:"SkyWalking_OAP_Cluster"}
    authentication: ${SW_CLUSTER_ETCD_AUTHENTICATION:false}
    user: ${SW_SCLUSTER_ETCD_USER:}
    password: ${SW_SCLUSTER_ETCD_PASSWORD:}
```

Same as the Zookeeper coordinator,
in some cases, the OAP default gRPC host and port in core are not suitable for internal communication among the oap nodes.
The following settings are provided to set the host and port manually, based on your own LAN env.
- internalComHost: The registered host and other OAP nodes use this to communicate with the current node.
- internalComPort: The registered port and other OAP nodes use this to communicate with the current node.

## Nacos
Set the **cluster/selector** to **nacos** in the yml to enable it.

```yaml
cluster:
  selector: ${SW_CLUSTER:nacos}
  # other configurations
```

Nacos supports authentication by username or accessKey. Empty means that there is no need for authentication. Extra config is as follows:
```yaml
nacos:
  username:
  password:
  accessKey:
  secretKey:
```

Same as the Zookeeper coordinator,
in some cases, the OAP default gRPC host and port in core are not suitable for internal communication among the OAP nodes.
The following settings are provided to set the host and port manually, based on your own LAN env.
- internalComHost: The registered host and other OAP nodes use this to communicate with the current node.
- internalComPort: The registered port and other OAP nodes use this to communicate with the current node.
