# Backend storage
The SkyWalking storage is pluggable. We have provided the following storage solutions, which allow you to easily
use one of them by specifying it as the `selector` in `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:banyandb}
```

## BanyanDB - Native APM Database
- [BanyanDB](storages/banyandb.md)

BanyanDB is a native-built SkyWalking database that focuses entirely on SkyWalking use cases.
BanyanDB demonstrates significant potential in improving performance and optimizing resource utilization. 
In typical deployment scenarios involving around 200 services and 200+ calls per second, a cluster configured with 2 liaison nodes and 2 data nodes—each equipped with 4 vCPUs and 8 GB memory—delivers stable and efficient performance.
BanyanDB also supports full tracing sampling, providing trace collection capabilities up to 100%, ensuring comprehensive observability without compromising system stability.

For the latest performance benchmarks of **BanyanDB**, please refer to the following sections:
- [**Single‑Model Benchmark (Trace / Log / Measure / Property)**](https://skywalking.apache.org/docs/skywalking-banyandb/next/operation/benchmark/benchmark-single-model/) — evaluates individual data models in isolation.
- [**Hybrid Scenario Benchmark — Realistic workloads for typical SkyWalking use cases**](https://skywalking.apache.org/docs/skywalking-banyandb/next/operation/benchmark/benchmark-hybrid/) — simulates mixed observability data ingestion and query scenarios.

## SQL database
- [MySQL and its compatible databases](storages/mysql.md)
- [PostgreSQL and its compatible databases](storages/postgresql.md)

MySQL and PostgreSQL are recommended for production environments for medium-scale deployments, especially for low trace
and log sampling rates. Some of their compatible databases may support larger scale better, such as TiDB and AWS Aurora. 
But the logs and traces performance could be significantly lower than BanyanDB and Elasticsearch, even more, their performance can't be improved linearly
through scaling out nodes.

## Elasticsearch+

- [OpenSearch](storages/elasticsearch.md#opensearch)
- [ElasticSearch 7 and 8](storages/elasticsearch.md#elasticsearch)

Elasticsearch and OpenSearch are recommended for production environments, especially for large-scale deployments.
OpenSearch derived from Elasticsearch 7.10.2 and iterates by its own roadmap. But notice, Elasticsearch cluster resource costs are high,
due to its high requirement for memory and replication requirement to keep cluster robustness.
