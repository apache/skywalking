# PostgreSQL
PostgreSQL JDBC driver uses version 42.3.2. It supports PostgreSQL 8.2 or newer.
Activate PostgreSQL as storage, and set storage provider to **postgresql**.

```yaml
storage:
  selector: ${SW_STORAGE:postgresql}
  postgresql:
    properties:
      jdbcUrl: ${SW_JDBC_URL:"jdbc:postgresql://localhost:5432/skywalking"}
      dataSource.user: ${SW_DATA_SOURCE_USER:postgres}
      dataSource.password: ${SW_DATA_SOURCE_PASSWORD:123456}
      dataSource.cachePrepStmts: ${SW_DATA_SOURCE_CACHE_PREP_STMTS:true}
      dataSource.prepStmtCacheSize: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE:250}
      dataSource.prepStmtCacheSqlLimit: ${SW_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT:2048}
      dataSource.useServerPrepStmts: ${SW_DATA_SOURCE_USE_SERVER_PREP_STMTS:true}
    metadataQueryMaxSize: ${SW_STORAGE_MYSQL_QUERY_MAX_SIZE:5000}
    maxSizeOfArrayColumn: ${SW_STORAGE_MAX_SIZE_OF_ARRAY_COLUMN:20}
    numOfSearchableValuesPerTag: ${SW_STORAGE_NUM_OF_SEARCHABLE_VALUES_PER_TAG:2}
    maxSizeOfBatchSql: ${SW_STORAGE_MAX_SIZE_OF_BATCH_SQL:2000}
    asyncBatchPersistentPoolSize: ${SW_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:4}
```
All connection-related settings, including URL link, username, and password, are found in `application.yml`.
Only part of the settings is listed here. Please follow [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool document for full settings.
