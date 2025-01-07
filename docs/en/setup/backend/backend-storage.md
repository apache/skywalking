# Backend storage
The SkyWalking storage is pluggable. We have provided the following storage solutions, which allow you to easily
use one of them by specifying it as the `selector` in `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:banyandb}
```

## BanyanDB - Native APM Database
- [BanyanDB](storages/banyandb.md)

BanyanDB is a native-built SkyWalking database, which can completely focus on SkyWalking use cases.
It has demonstrated significant potential for performance improvement and reduced resource usage requirements. It indicates 5x less memory usage, 
1/5 disk IOPS, 1/4 disk throughput, and 30% less disk space, albeit with a slightly higher CPU trade-off, compared to Elasticsearch.

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
