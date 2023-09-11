# MySQL
Activate MySQL as storage, and set storage provider to **mysql**.

**NOTE:** MySQL driver is NOT allowed in Apache official distribution and source codes.
Please download the MySQL driver on your own. Copy the connection driver jar to `oap-libs`.

```yaml
storage:
  selector: ${SW_STORAGE:mysql}
  mysql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:mysql://localhost:3306/swtest?rewriteBatchedStatements=true&allowMultiQueries=true"}
      dataSource.user: ${SW_DATA_SOURCE_USER:root}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:root@1234}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
    maxSizeOfBatchSql: ${SW_STORAGE_MAX_SIZE_OF_BATCH_SQL:2000}
    asyncBatchPersistentPoolSize: ${SW_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:4}
```
All connection-related settings, including URL link, username, and password, are found in `application.yml`.
Only part of the settings is listed here. See the [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for full settings.
To understand the function of the parameter `rewriteBatchedStatements=true` in MySQL, see the [MySQL official document](https://dev.mysql.com/doc/connector-j/8.0/en/connector-j-connp-props-performance-extensions.html#cj-conn-prop_rewriteBatchedStatements).

In theory, all other databases that are compatible with MySQL protocol should be able to use this storage plugin,
such as TiDB. Please compose the JDBC URL according to the database's documentation.
