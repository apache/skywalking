# Backend storage
The SkyWalking storage is pluggable. We have provided the following storage solutions, which allow you to easily
use one of them by specifying it as the `selector` in `application.yml`：

```yaml
storage:
  selector: ${SW_STORAGE:elasticsearch}
```

Natively supported storage:
- [H2](storages/h2.md)
- [OpenSearch](storages/elasticsearch.md#opensearch)
- [ElasticSearch 7 and 8](storages/elasticsearch.md#elasticsearch). 
- [MySQL and its compatible databases](storages/mysql.md)
- [PostgreSQL and its compatible databases](storages/postgresql.md)
- [BanyanDB(alpha stage)](storages/banyandb.md)

H2 is the default storage option in the distribution package. It is recommended to use H2 for testing and development ONLY.
Elasticsearch and OpenSearch are recommended for production environments, specially for large scale deployments.
MySQL and PostgreSQL are recommended for production environments for medium scale deployments, especially for low trace
and log sampling rate. Some of their compatible databases may support larger scale better, such as TiDB and AWS Aurora.

BanyanDB is going to be our next generation storage solution. It is still in alpha stage. It has shown high potential performance
improvement. Less than 50% CPU usage and 50% memory usage with 40% disk volume compared to Elasticsearch in the same scale with 100% sampling.
We are looking for early adoption, and it would be our first-class recommended storage option since 2024.
