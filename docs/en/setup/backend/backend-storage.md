# Backend storage
The SkyWalking storage is pluggable. We have provided the following storage solutions, which allow you to easily
use one of them by specifying it as the `selector` in `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
```

Natively supported storage:

## BanyanDB - Native APM Database
- [BanyanDB](storages/banyandb.md)

This is recommended to use for medium scale deployments from 0.6 until 1.0.
It has demonstrated significant potential in performance improvement. As of version 0.6.1, it achieves 5x less memory usage, 
1/5 disk IOPS, 1/4 disk throughput, and 30% less disk space, albeit with a slightly higher CPU trade-off, compared to Elasticsearch.
We are looking for early adoption. Please contact us through Slack channels if you are interested in using BanyanDB.

## SQL database
- [H2](storages/h2.md)

H2 is the default storage option in the distribution package. It is recommended to use H2 for testing and development ONLY.

- [MySQL and its compatible databases](storages/mysql.md)
- [PostgreSQL and its compatible databases](storages/postgresql.md)

MySQL and PostgreSQL are recommended for production environments for medium scale deployments, especially for low trace
and log sampling rate. Some of their compatible databases may support larger scale better, such as TiDB and AWS Aurora.

## Elasticsearch+

- [OpenSearch](storages/elasticsearch.md#opensearch)
- [ElasticSearch 7 and 8](storages/elasticsearch.md#elasticsearch)

Elasticsearch and OpenSearch are recommended for production environments, specially for large scale deployments.
OpenSearch derived from Elasticsearch 7.10.2 and iterate by its own roadmap.
