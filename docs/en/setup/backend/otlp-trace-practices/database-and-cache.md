# Database and Cache

A database or a cache reports no traces of its own. The trace analysis names it after the address a client's span
reports: the OpenTelemetry trace analysis by the peer a CLIENT span names, under `OTLP_VIRTUAL_DATABASE`, or
`OTLP_VIRTUAL_CACHE` for Redis, Valkey and memcached, see [Topology](../trace-analyzer.md#topology); the native trace
analysis by an exit span's peer, under `VIRTUAL_DATABASE` or `VIRTUAL_CACHE`. A server that clients reach at more
than one address shows as one node per address, and can be named as one cluster in [server
clusters](../server-clusters.md).

## Naming a database or cache cluster

Only an address a span reports is renamed, so what to list follows from the agent.

The SkyWalking Java agent:

| Client | Reports | List |
|---|---|---|
| JDBC with one host, Jedis, Lettuce, Redisson with a single server, Neo4j | the node | every node, as members |
| JDBC with several hosts: MySQL, MariaDB, PostgreSQL, SQL Server, an Oracle address list | every host, joined by `,`, with the default port added where the URL has none | every host |
| JedisCluster 2.x and 3.x | every seed, each followed by `;` | every seed |
| Jedis 4.x cluster or sharded | every node, joined by `,` | every node |
| Redisson cluster, replicated, or master and slaves | the configured nodes, joined by `;`; in sentinel mode, the sentinels | every node, or every sentinel |
| MongoDB | the servers it knows of, joined by `;` | every server |
| Elasticsearch | the nodes, joined by `,`; the 5.x transport client, each followed by `;` | every node |
| Cassandra | the contact points, joined by `,` | every contact point |
| spymemcached, xmemcached | the servers, joined by `;` | every server |

JedisCluster 2.x and 3.x and Redisson cut what they report to the agent's `plugin.peer_max_length`
(`SW_PLUGIN_PEER_MAX_LENGTH`), 200 characters by default, and end it with `...`. A longer list, four nodes named
`redis-0.redis-headless.prod.svc.cluster.local:6379` already, ends in part of an address and is not renamed; raise the
limit above the length of the whole list.

The OpenTelemetry agents:

| Client | Reports | List |
|---|---|---|
| Java agent: Jedis, Lettuce, Redisson, MongoDB, Cassandra, Spymemcached, Elasticsearch REST | the node it contacted | every node, as members |
| Java agent with its stable database conventions on | the configured hosts joined by `,`, at most five: in the configured order, sorted for a Redis cluster and Cassandra, with a port only where one is not the default; a Redis sentinel target as `s1,s2/mymaster` | every host, in each spelling it is reported in; a sentinel target whole, as an alias |
| Python DB-API drivers and SQLAlchemy; JavaScript pg, mysql, mysql2, ioredis and redis; .NET SqlClient | the host and port of the connection, as configured | that address, an alias when it names a Service |
| pymongo, asyncpg; JavaScript mongodb, cassandra-driver and memcached | the node it contacted | every node, as members |

A span that reports no address the trace analysis reads keeps its name: the OpenTelemetry Java agent's Elasticsearch
transport and OpenSearch clients and Go's otelmongo report the node only as `network.peer.address`, and .NET's
Entity Framework Core only as `peer.service`.

## Renaming a cluster only for its own system

The shipped rules call `findServerClusterName(address)` without a kind, and the native trace analysis does not use
kinds, so a client span of any database or cache system is renamed to the cluster that lists its address. A TAL rule
file of your own can rename a cluster only for the clients of its own system. Give each cluster, as kinds, the system
its clients report as `db.system.name` or `db.system`:

```yaml
clusters:
  - name: orders-db
    kinds: [postgresql]
    members:
      - pg-0.pg.db.svc.cluster.local:5432
      - pg-1.pg.db.svc.cluster.local:5432
  - name: session-cache
    kinds: [redis]
    members:
      - redis-0.redis.cache.svc.cluster.local:6379
      - redis-1.redis.cache.svc.cluster.local:6379
```

Then copy `config/tal/otlp-general.yaml` to a file of your own, such as `config/tal/otlp-custom.yaml`, list that file
in `talFiles` in place of `otlp-general`, see [Configuration](../trace-analyzer.md#configuration), and pass the system
at each of its three calls:

```text
// otlp-general, the consumer block: the broker a message came through
def cluster = findServerClusterName(broker, attr("messaging.system.name", "messaging.system"))
// otlp-general, the outbound block: the database, cache or broker a client or producer calls
def cluster = findServerClusterName(peer, attr("db.system.name", "db.system", "messaging.system.name", "messaging.system"))
// otlp-slow-sql: the database a slow statement is filed under; system is the span's db.system.name or db.system
def cluster = findServerClusterName(peer, system)
```

Change the three together. The outbound call names databases, caches and brokers alike, so a rule that passes a kind
there and not at the consumer, or not in `otlp-slow-sql`, names one server two ways. Each passes the system as the span
reports it, so the two sides of a call meet at the same cluster. Kinds are compared as written. A system the conventions
renamed needs both spellings when its clients report both, such as SQL Server's `kinds: [mssql, microsoft.sql_server]`.
A cluster that lists no kinds is still renamed for every system. The OAP reads the rule files at start, so restart it
after the change.
