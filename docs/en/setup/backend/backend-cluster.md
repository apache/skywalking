# Cluster Management

In many production environments, the backend needs to support **distributed aggregation**, high throughput 
and provide high availability (HA) to maintain robustness, so **you always need to setup CLUSTER management in product env**.
Otherwise, you would face metrics **inaccurate**.

`core/gRPCHost` is listening on `0.0.0.0` for quick start as the single mode for most cases.
Besides the `Kubernetes` coordinator, which is using the cloud-native mode to establish cluster, all other coordinators
requires `core/gRPCHost` updated to real IP addresses or take reference of `internalComHost` and `internalComPort` in each
coordinator doc.

NOTICE, cluster management doesn't provide a service discovery mechanism for agents and probes. We recommend
agents/probes using gateway to load balancer to access OAP clusters.

There are various ways to manage the cluster in the backend. Choose the one that best suits your needs.

- [Kubernetes](#kubernetes). When the backend clusters are deployed inside Kubernetes, you could make use of this method
  by using k8s native APIs to manage clusters.
- [Zookeeper coordinator](#zookeeper-coordinator). Use Zookeeper to let the backend instances detect and communicate
  with each other.
- [Consul](#consul). Use Consul as the backend cluster management implementor and coordinate backend instances.
- [Etcd](#etcd). Use Etcd to coordinate backend instances.
- [Nacos](#nacos). Use Nacos to coordinate backend instances.

In the `application.yml` file, there are default configurations for the aforementioned coordinators under the
section `cluster`. You can specify any of them in the `selector` property to enable it.

# Cloud Native
## Kubernetes

The required backend clusters are deployed inside Kubernetes. See the guides in [Deploy in kubernetes](backend-k8s.md).
Set the selector to `kubernetes`.

```yaml
cluster:
  selector: ${SW_CLUSTER:kubernetes}
  # other configurations
```
Meanwhile, the OAP cluster requires the pod's UID which is laid at `metadata.uid` as the value of the system environment variable **SKYWALKING_COLLECTOR_UID**

```yaml
containers:
  # Original configurations of OAP container
  - name: {{ .Values.oap.name }}
    image: {{ .Values.oap.image.repository }}:{{ required "oap.image.tag is required" .Values.oap.image.tag }}
    # ...
    # ...
    env:
    # Add metadata.uid as the system environment variable, SKYWALKING_COLLECTOR_UID 
    - name: SKYWALKING_COLLECTOR_UID
      valueFrom:
        fieldRef:
          fieldPath: metadata.uid
```

Read [the complete helm](https://github.com/apache/skywalking-helm/blob/476afd51d44589c77a4cbaac950272cd5d064ea9/chart/skywalking/templates/oap-deployment.yaml#L125) for more details.

# Traditional Coordinator

**NOTICE**
In all the following coordinators, `oap.internal.comm.host`:`oap.internal.comm.port` is registered as the ID
and address for the current OAP node. By default, because they are same in all OAP nodes, the registrations are conflicted,
and (may) show as one registered node, which actually would be the node itself. **In this case, the cluster mode is NOT working.**

Please check the registered nodes on your coordinator servers, to make the registration information unique for every node.
You could have two options

1. Change `core/gRPCHost`(`oap.internal.comm.host`) and `core/gRPCPort`(`oap.internal.comm.port`) for internal,
   and [setup external communication channels](backend-expose.md) for data reporting and query.
2. Use `internalComHost` and `internalComPort` in the config to provide a unique host and port for every OAP node. This
   host name port should be accessible for other OAP nodes.

## Zookeeper coordinator

Zookeeper is a very common and widely used cluster coordinator. Set the **cluster/selector** to **zookeeper** in the yml
to enable it.

Required Zookeeper version: 3.5+

```yaml
cluster:
  selector: ${SW_CLUSTER:zookeeper}
  # other configurations
```

- `hostPort` is the list of zookeeper servers. Format is `IP1:PORT1,IP2:PORT2,...,IPn:PORTn`
- `enableACL`
  enable [Zookeeper ACL](https://zookeeper.apache.org/doc/r3.5.5/zookeeperProgrammers.html#sc_ZooKeeperAccessControl) to
  control access to its znode.
- `schema` is Zookeeper ACL schemas.
- `expression` is a expression of ACL. The format of the expression is specific to
  the [schema](https://zookeeper.apache.org/doc/r3.5.5/zookeeperProgrammers.html#sc_BuiltinACLSchemes).
- `hostPort`, `baseSleepTimeMs` and `maxRetries` are settings of Zookeeper curator client.

Note:

- If `Zookeeper ACL` is enabled and `/skywalking` exists, you must ensure that `SkyWalking` has `CREATE`, `READ`
  and `WRITE` permissions. If `/skywalking` does not exist, it will be created by SkyWalking, and all permissions to the
  specified user will be granted. Simultaneously, znode grants READ permission to anyone.
- If you set `schema` as `digest`, the password of the expression is set in **clear text**.

In some cases, the OAP default gRPC host and port in the core are not suitable for internal communication among the OAP
nodes, such as the default host(`0.0.0.0`) should not be used in cluster mode.
The following settings are provided to set the host and port manually, based on your own LAN env.

- internalComHost: The exposed host name for other OAP nodes in the cluster internal communication.
- internalComPort: the exposed port for other OAP nodes in the cluster internal communication.

```yaml
cluster:
  selector: ${SW_CLUSTER:zookeeper}
  ...
  zookeeper:
    namespace: ${SW_NAMESPACE:""}
    hostPort: ${SW_CLUSTER_ZK_HOST_PORT:localhost:2181}
    #Retry Policy
    baseSleepTimeMs: ${SW_CLUSTER_ZK_SLEEP_TIME:1000} # initial amount of time to wait between retries
    maxRetries: ${SW_CLUSTER_ZK_MAX_RETRIES:3} # max number of times to retry
    internalComHost: ${SW_CLUSTER_INTERNAL_COM_HOST:172.10.4.10}
    internalComPort: ${SW_CLUSTER_INTERNAL_COM_PORT:11800}
    # Enable ACL
    enableACL: ${SW_ZK_ENABLE_ACL:false} # disable ACL in default
    schema: ${SW_ZK_SCHEMA:digest} # only support digest schema
    expression: ${SW_ZK_EXPRESSION:skywalking:skywalking}
```

## Consul

Recently, the Consul system has become more and more popular, and many companies and developers now use Consul as
their service discovery solution. Set the **cluster/selector** to **consul** in the yml to enable it.

```yaml
cluster:
  selector: ${SW_CLUSTER:consul}
  ...
  consul:
  serviceName: ${SW_SERVICE_NAME:"SkyWalking_OAP_Cluster"}
  # Consul cluster nodes, example: 10.0.0.1:8500,10.0.0.2:8500,10.0.0.3:8500
  hostPort: ${SW_CLUSTER_CONSUL_HOST_PORT:localhost:8500}
  aclToken: ${SW_CLUSTER_CONSUL_ACLTOKEN:""}
  internalComHost: ${SW_CLUSTER_INTERNAL_COM_HOST:""}
  internalComPort: ${SW_CLUSTER_INTERNAL_COM_PORT:-1}
```

Same as the Zookeeper coordinator,
in some cases, the OAP default gRPC host and port in the core are not suitable for internal communication among the OAP
nodes, such as the default host(`0.0.0.0`) should not be used in cluster mode.
The following settings are provided to set the host and port manually, based on your own LAN env.

- internalComHost: The exposed host name for other OAP nodes in the cluster internal communication.
- internalComPort: the exposed port for other OAP nodes in the cluster internal communication.

## Etcd

Set the **cluster/selector** to **etcd** in the yml to enable it. The Etcd client has upgraded to v3 protocol and
changed to the CoreOS official library. **Since 8.7.0, only the v3 protocol is supported for Etcd.**

```yaml
cluster:
  selector: ${SW_CLUSTER:etcd}
  # other configurations
  etcd:
    # etcd cluster nodes, example: 10.0.0.1:2379,10.0.0.2:2379,10.0.0.3:2379
    endpoints: ${SW_CLUSTER_ETCD_ENDPOINTS:localhost:2379}
    namespace: ${SW_CLUSTER_ETCD_NAMESPACE:/skywalking}
    serviceName: ${SW_CLUSTER_ETCD_SERVICE_NAME:"SkyWalking_OAP_Cluster"}
    authentication: ${SW_CLUSTER_ETCD_AUTHENTICATION:false}
    user: ${SW_CLUSTER_ETCD_USER:}
    password: ${SW_CLUSTER_ETCD_PASSWORD:}
```

Same as the Zookeeper coordinator,
in some cases, the OAP default gRPC host and port in the core are not suitable for internal communication among the OAP
nodes, such as the default host(`0.0.0.0`) should not be used in cluster mode.
The following settings are provided to set the host and port manually, based on your own LAN env.

- internalComHost: The exposed host name for other OAP nodes in the cluster internal communication.
- internalComPort: the exposed port for other OAP nodes in the cluster internal communication.

## Nacos

Set the **cluster/selector** to **nacos** in the yml to enable it.

**The Nacos client has upgraded to 2.x, so required Nacos Server version is  2.x**.

```yaml
cluster:
  selector: ${SW_CLUSTER:nacos}
  # other configurations
```

Nacos supports authentication by username or accessKey. Empty means that there is no need for authentication. Extra
config is as follows:

```yaml
nacos:
  username:
  password:
  accessKey:
  secretKey:
```

Same as the Zookeeper coordinator,
in some cases, the OAP default gRPC host and port in the core are not suitable for internal communication among the OAP
nodes, such as the default host(`0.0.0.0`) should not be used in cluster mode.
The following settings are provided to set the host and port manually, based on your own LAN env.

- internalComHost: The exposed host name for other OAP nodes in the cluster internal communication.
- internalComPort: the exposed port for other OAP nodes in the cluster internal communication.
