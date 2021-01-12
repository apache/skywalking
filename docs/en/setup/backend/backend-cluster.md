# Cluster Management
In many product environments, backend needs to support high throughput and provides HA to keep robustness,
so you should need cluster management always in product env.
 
Backend provides several ways to do cluster management. Choose the one you need/want.

- [Zookeeper coordinator](#zookeeper-coordinator). Use Zookeeper to let backend instance detects and communicates
with each other.
- [Kubernetes](#kubernetes). When backend cluster are deployed inside kubernetes, you could choose this
by using k8s native APIs to manage cluster.
- [Consul](#consul). Use Consul as backend cluster management implementor, to coordinate backend instances.
- [Etcd](#etcd). Use Etcd to coordinate backend instances.
- [Nacos](#nacos). Use Nacos to coordinate backend instances.
In the `application.yml`, there're default configurations for the aforementioned coordinators under the section `cluster`,
you can specify one of them in the `selector` property to enable it.

## Zookeeper coordinator
Zookeeper is a very common and wide used cluster coordinator. Set the **cluster/selector** to **zookeeper** in the yml to enable.

Required Zookeeper version, 3.4+

```yaml
cluster:
  selector: ${SW_CLUSTER:zookeeper}
  # other configurations
```

- `hostPort` is the list of zookeeper servers. Format is `IP1:PORT1,IP2:PORT2,...,IPn:PORTn`
- `enableACL` enable [Zookeeper ACL](https://zookeeper.apache.org/doc/r3.4.1/zookeeperProgrammers.html#sc_ZooKeeperAccessControl) to control access to its znode.
- `schema` is Zookeeper ACL schemas.
- `expression` is a expression of ACL. The format of the expression is specific to the [schema](https://zookeeper.apache.org/doc/r3.4.1/zookeeperProgrammers.html#sc_BuiltinACLSchemes). 
- `hostPort`, `baseSleepTimeMs` and `maxRetries` are settings of Zookeeper curator client.

Note: 
- If `Zookeeper ACL` is enabled and `/skywalking` existed, must be sure `SkyWalking` has `CREATE`, `READ` and `WRITE` permissions. If `/skywalking` is not exists, it will be created by SkyWalking and grant all permissions to the specified user. Simultaneously, znode is granted READ to anyone.
- If set `schema` as `digest`, the password of expression is set in **clear text**. 

In some cases, oap default gRPC host and port in core are not suitable for internal communication among the oap nodes.
The following setting are provided to set the host and port manually, based on your own LAN env.
- internalComHost, the host registered and other oap node use this to communicate with current node.
- internalComPort, the port registered and other oap node use this to communicate with current node.

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
Require backend cluster are deployed inside kubernetes, guides are in [Deploy in kubernetes](backend-k8s.md).
Set the selector to `kubernetes`.

```yaml
cluster:
  selector: ${SW_CLUSTER:kubernetes}
  # other configurations
```

## Consul
Now, consul is becoming a famous system, many of companies and developers using consul to be 
their service discovery solution. Set the **cluster/selector** to **consul** in the yml to enable.

```yaml
cluster:
  selector: ${SW_CLUSTER:consul}
  # other configurations
```

Same as Zookeeper coordinator,
in some cases, oap default gRPC host and port in core are not suitable for internal communication among the oap nodes.
The following setting are provided to set the host and port manually, based on your own LAN env.
- internalComHost, the host registered and other oap node use this to communicate with current node.
- internalComPort, the port registered and other oap node use this to communicate with current node.


## Etcd
Set the **cluster/selector** to **etcd** in the yml to enable.

```yaml
cluster:
  selector: ${SW_CLUSTER:etcd}
  # other configurations
```

Same as Zookeeper coordinator,
in some cases, oap default gRPC host and port in core are not suitable for internal communication among the oap nodes.
The following setting are provided to set the host and port manually, based on your own LAN env.
- internalComHost, the host registered and other oap node use this to communicate with current node.
- internalComPort, the port registered and other oap node use this to communicate with current node.

## Nacos
Set the **cluster/selector** to **nacos** in the yml to enable.

```yaml
cluster:
  selector: ${SW_CLUSTER:nacos}
  # other configurations
```

Nacos support authenticate by username or accessKey, empty means no need auth. extra config is bellow:
```yaml
nacos:
  username:
  password:
  accessKey:
  secretKey:
```

Same as Zookeeper coordinator,
in some cases, oap default gRPC host and port in core are not suitable for internal communication among the oap nodes.
The following setting are provided to set the host and port manually, based on your own LAN env.
- internalComHost, the host registered and other oap node use this to communicate with current node.
- internalComPort, the port registered and other oap node use this to communicate with current node.