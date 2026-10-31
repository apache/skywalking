# Server Clusters

A message broker, a database or a cache is an uninstrumented server: it reports no traces of its own, and the trace
analysis names it after the address the clients' spans report. A server that clients reach at more than one address,
one node or another, a Kubernetes Service, a load balancer, or the whole list of nodes a client was configured with,
then shows as one node per address. List it in `server-clusters.yml`, or through the [dynamic
configuration](dynamic-config.md), to show it as one service named after the cluster.

The file is empty by default, so nothing is renamed until a cluster is listed. A match renames the service only; no
instance is generated for the addresses.

## Configuration format

```yaml
clusters:
  - name: kafka-prod                         # the service the topology shows
    kinds: [kafka, queue]                    # optional: names a custom TAL rule may filter by
    members:                                 # addresses of single nodes, as the clients report them
      - kafka-0.kafka-headless.prod.svc.cluster.local:9092
      - kafka-1.kafka-headless.prod.svc.cluster.local:9092
    aliases:                                 # addresses that name the whole cluster: a Service, a load balancer,
      - kafka.prod.svc.cluster.local:9092    # a bootstrap entry
  - name: pulsar-prod
    scheme: pulsar://                        # optional: the prefix the clients write before an address
    members:
      - pulsar-0.pulsar.prod.svc.cluster.local:6650
      - pulsar-1.pulsar.prod.svc.cluster.local:6650
  - name: orders-db
    members:
      - pg-0.pg.db.svc.cluster.local:5432
      - pg-1.pg.db.svc.cluster.local:5432
```

- `name` is the service name. It must be unique among all service names, since a service is identified by its name
  alone, whatever its layer, and no longer than `core.default.serviceNameMaxLength`, 70 characters by default.
- `members` and `aliases` are both renamed to the cluster. Members are the addresses of single nodes, aliases those
  that name the whole cluster; an address is listed once across all clusters.
- `scheme` is the prefix the cluster's clients write before an address, in full: `pulsar://` for a Pulsar client
  that reports `pulsar://h1:6650`, `nats://` for a NATS client that reports `nats://h1:4222`. It is compared as
  written, whatever the prefix is, so a cluster that declares `pulsar://` does not match the `pulsar+ssl://` URL a
  Pulsar client uses for TLS. The addresses are listed without it.
- `kinds` are names of your choosing, such as `postgresql`, `kafka` or `queue`, for custom TAL rules. A rule that
  passes one to `findServerClusterName(address, kind)` matches only the clusters that list it, and those that list
  no kind; the best practices for [message
  queues](otlp-trace-practices/message-queue.md#renaming-a-broker-cluster-only-for-its-own-system) and for [databases
  and caches](otlp-trace-practices/database-and-cache.md#renaming-a-cluster-only-for-its-own-system) pass the system
  a span reports. The native trace analysis does not use kinds, and the shipped TAL rules pass none, so both match
  every cluster, whatever its kinds.

Every entry is a string. Quote one that YAML would read as another type, such as `on`, which YAML reads as a boolean,
or a bare number.

A file or a dynamic value that breaks these rules is rejected as a whole: an invalid file stops the OAP from starting,
and an invalid dynamic value is logged while the previous clusters stay in use.

## How an address matches

1. The whole reported string equals a member or an alias.
2. Otherwise, the string is a list of the elements between `,` and `;`, or a single element. Each element is
   trimmed, an empty element, as a trailing separator leaves, is skipped, and each element is matched as reported,
   then, when it begins with a cluster's `scheme`, by the rest of it, under the cluster that declares that `scheme`. So
   `pulsar://h1:6650`, `pulsar://h1:6650,h2:6650` and `nats://h1:4222,nats://h2:4222` all match. The string is
   renamed when every element is listed under the same cluster, in any order and however many of the cluster's
   addresses it names. A list with an element that is listed nowhere, or under another cluster, keeps its name.

Addresses are compared as strings. Nothing is resolved, in either direction, and no wildcard or default port is
assumed, so list every spelling the clients report:

- A Kubernetes Service is reached as `kafka`, `kafka.prod`, `kafka.prod.svc` or `kafka.prod.svc.cluster.local`, and
  a pod of a headless Service as `kafka-0.kafka-headless.prod.svc.cluster.local`, each a different string.
- Agents leave a default port out of the address, so a host without its port is a spelling of its own.
- Some clients report the IP address of the node they connected to. A pod's IP address changes when the pod is
  replaced, so prefer DNS names where the clients report them; an IP address suits a server whose address is stable.

A span that reports no address, whose server is named after its messaging or database system, is not renamed.

## Where the rename applies

- The native trace analysis, for the spans of the SkyWalking agents: the database, cache or broker an exit span
  calls, the broker a consumer's message came through, and the virtual database, cache and message queue services
  with their topics, queues, slow statements and slow commands, whatever the cluster's kinds. An exit span whose
  address a network alias maps to an instrumented service keeps that service as its destination.
- The OpenTelemetry trace analysis, wherever a TAL rule calls
  [`findServerClusterName(address)`](../../concepts-and-designs/tal.md#global-functions), or
  `findServerClusterName(address, kind)` to match only the clusters of a kind. The shipped
  `tal/otlp-general.yaml` calls it, without a kind, for the broker of a producer and of a consumer, and for the
  database or cache a client span calls, and `otlp-slow-sql` for the database a slow statement is filed under. It
  asks only for an address the span reports, not for a peer named by `service.peer.name` or `peer.service`.

What the agents report for a cluster, and what to list for them, is described in the best practices for [message
queues](otlp-trace-practices/message-queue.md#naming-a-broker-cluster) and for [databases and
caches](otlp-trace-practices/database-and-cache.md).

Data recorded before a cluster is listed stays under the address it was recorded with.

## Dynamic configuration

The item `core.default.serverClusters` replaces the file's content, in the same format. Removing the item restores
the file's content. See [dynamic configuration](dynamic-config.md).
