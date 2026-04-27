# Upgrading to BanyanDB 0.10.0

This page summarizes the BanyanDB 0.10.0 changes that affect SkyWalking OAP operators and integrators.
For the full upstream change list, see the [BanyanDB 0.10.0 release notes](https://github.com/apache/skywalking-banyandb/releases/tag/v0.10.0)
and the upstream [Upgrading to 0.10](https://skywalking.apache.org/docs/skywalking-banyandb/latest/operation/upgrade/) guide.

## Compatibility

OAP defaults to BanyanDB **API version 0.10**. The compatible API versions are exposed via:

```
${SW_STORAGE_BANYANDB_COMPATIBLE_SERVER_API_VERSIONS:"0.10"}
```

The corresponding BanyanDB server release is **0.10.x**. Refer to the
[API versions mapping](https://skywalking.apache.org/docs/skywalking-banyandb/latest/installation/versions/)
for the OAP/BanyanDB compatibility matrix.

If the running BanyanDB server reports an incompatible API version, OAP refuses to start with:

```
... ERROR [] - ... Incompatible BanyanDB server API version: 0.x. But accepted versions: 0.y
```

## Breaking changes that affect SkyWalking deployments

### 1. Property data on-disk path layout

The on-disk path for property data now includes the group name:

- **Before (0.9.x):** `<data-dir>/property/data/shard-<id>/...`
- **After (0.10.x):** `<data-dir>/property/data/<group>/shard-<id>/...`

OAP stores UI templates, profiling task metadata and similar items in the BanyanDB `property` group
(see the `property` block in [bydb.yml](../setup/backend/storages/banyandb.md#configuration)).
Existing data at the old path is **not** migrated automatically. Operators with persisted property data
should either:

- accept that the existing property contents will be re-created by OAP after the upgrade
  (UI templates, async profiler tasks, etc. are re-initialized), or
- follow the upstream [Upgrading to 0.10](https://skywalking.apache.org/docs/skywalking-banyandb/latest/operation/upgrade/)
  procedure for any data you need to preserve.

### 2. Node discovery default changed to `none`

The default BanyanDB node discovery mode changed from `etcd` to `none`. **Standalone deployments are unaffected**
because they do not use node discovery.

For **cluster deployments** (the multi-`liaison`/`data` setup that OAP connects to via the `targets` list in
[bydb.yml](../setup/backend/storages/banyandb.md#configuration)), every BanyanDB node must now be started
with an explicit discovery mode, e.g.:

```shell
banyand data --node-discovery-mode=etcd      # previous default
banyand data --node-discovery-mode=dns       # Kubernetes-friendly
banyand data --node-discovery-mode=file      # static configuration
```

Without this flag, cluster nodes will not discover each other and OAP write/query traffic to the cluster will fail.
See the upstream [Node Discovery](https://skywalking.apache.org/docs/skywalking-banyandb/latest/operation/node-discovery/)
guide for the full list of modes and flags.

### 3. Windows binaries no longer shipped

BanyanDB 0.10.0 releases no longer ship Windows binaries or Docker images. SkyWalking deployments running BanyanDB
on Windows must build BanyanDB from source. Linux and macOS deployments are unaffected.

## Behavioral changes worth knowing

### Property background repair on by default

BanyanDB 0.10.0 enables the property background repair mechanism by default. This automatically repairs inconsistent
property data across nodes via a gossip protocol. No SkyWalking-side configuration change is required.
The mechanism can be tuned/disabled via BanyanDB-side flags such as `--property-repair-enabled`,
`--property-repair-cron` and `--property-repair-history-days`. See the upstream
[Property Repair](https://skywalking.apache.org/docs/skywalking-banyandb/latest/operation/property-repair/) guide.

### Stream element IDs

BanyanDB 0.10.0 generates `element_id` server-side when omitted, and deduplicates stream query results by
`element_id`. SkyWalking writes (logs, segments, browser error logs, etc.) continue to work without changes;
the change reduces duplicate rows that can otherwise appear under retry or replay scenarios.

### Distributed measure aggregation

For cluster deployments, BanyanDB 0.10.0 pushes measure aggregation work down to data nodes (map) and reduces
results on the liaison (reduce). SkyWalking metric queries benefit transparently. No OAP configuration change
is required, but cluster operators may observe shifted CPU/memory profiles between liaison and data nodes.

### Query/storage efficiency

- Bloom filters are no longer used for dictionary-encoded tags; an O(1) dictionary lookup is used instead.
- Tags referenced only in `WHERE` conditions no longer need to be present in the projection list.

These are transparent to OAP but may shift query latency for power users who issue ad-hoc queries directly
against BanyanDB via `bydbctl` or BydbQL.

## See also

- BanyanDB-side configuration: [Storage BanyanDB](../setup/backend/storages/banyandb.md)
- Data lifecycle: [Data Lifecycle Stages (Hot/Warm/Cold)](stages.md), [Progressive TTL](ttl.md)
- Self observability: [BanyanDB self observability dashboard](dashboards-banyandb.md)
- Upstream: [BanyanDB 0.10.0 release notes](https://github.com/apache/skywalking-banyandb/releases/tag/v0.10.0)
