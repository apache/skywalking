# Apache Doris Storage

This document describes how to use Apache Doris as the storage backend for Apache SkyWalking.

## Overview

Apache Doris is a real-time analytical database based on MPP architecture, known for its high performance and ease of use. This plugin allows SkyWalking to leverage Doris for storing its monitoring data.

## Configuration

To enable Apache Doris as the storage backend, you need to modify the `application.yml` configuration file.

Set the storage selector to `doris`:

```yaml
storage:
  selector: ${SW_STORAGE:doris}
  # Other storage options like componentScanPkg can be here if needed
```

Then, configure the specific parameters for the Doris storage provider. These settings are typically placed under the `storage.doris` section in `application.yml`.

### Parameters

The following parameters are available for the Doris storage plugin, corresponding to the `StorageModuleDorisConfig` class:

| Parameter | Type   | Description                                     | Default Value |
|-----------|--------|-------------------------------------------------|---------------|
| `host`    | String | The hostname or IP address of your Doris FE server. | `localhost` (example) |
| `port`    | int    | The query port of your Doris FE server.           | `9030` (example)  |
| `user`    | String | The username for connecting to Doris.             | `root` (example)  |
| `password`| String | The password for the specified Doris user.        | `""` (empty)    |
| `database`| String | The database name in Doris to be used by SkyWalking. | `skywalking` (example) |

*(Note: Default values mentioned above are illustrative examples and might not be the actual defaults in the code if not explicitly set.)*

### Example Configuration

Here is an example configuration block for `application.yml`:

```yaml
storage:
  selector: ${SW_STORAGE:doris}
  doris:
    host: your_doris_fe_host
    port: 9030 # Default Doris query port
    user: your_doris_user
    password: your_doris_password
    database: skywalking_db
    # Optional: Add any other relevant config parameters here if they get added in the future
```

Make sure to replace placeholder values like `your_doris_fe_host` with your actual Doris setup details.

## Table Creation

Before starting the SkyWalking OAP server with the Doris storage plugin enabled, you must create the necessary tables and schemas in your Doris database. SkyWalking does not automatically create these tables in Doris.

The Data Definition Language (DDL) scripts for creating these tables are provided in the SkyWalking distribution under the Doris storage plugin directory:
`oap-server/server-storage-plugin/storage-doris-plugin/src/main/resources/doris_schema.sql`.

You need to execute the SQL commands in this `doris_schema.sql` file in your Apache Doris cluster before starting the OAP server.

**Important Considerations for `doris_schema.sql`:**
- The provided script is a basic starting point suitable for development or initial testing.
- **For production environments, you MUST review and customize the DDL.** Pay close attention to:
    - **Replication Factor:** Adjust the `replication_allocation` property (or `replication_num` for older Doris versions) for each table based on your cluster's fault tolerance requirements.
    - **Partitioning:** For time-series tables like `metrics_all`, `segment`, and `log_record`, implement appropriate time-based partitioning strategies (e.g., daily or weekly partitions on `time_bucket` or `timestamp` columns). This is crucial for query performance and efficient data lifecycle management (e.g., TTL for old data).
    - **Bucketing:** The number of buckets (`BUCKETS N`) in the `DISTRIBUTED BY HASH(...)` clause should be adjusted based on your Doris cluster size and expected data volume per partition to ensure even data distribution.
    - **Data Types & Lengths:** Verify that `VARCHAR` lengths, numeric precision, and other data types are suitable for your specific data characteristics.
    - **Key Models & Indexes:** While basic keys are defined, you may need additional secondary indexes or to adjust key models (DUPLICATE, UNIQUE, AGGREGATE) based on your primary query patterns. Consider creating materialized views (rollups) for frequently aggregated metrics.

Failure to create and appropriately configure the tables beforehand will result in errors during SkyWalking startup or suboptimal performance.

Consult the Apache Doris documentation for best practices on table design, schema properties, and data loading.

## Development Note

The SkyWalking Doris storage plugin utilizes the MySQL JDBC driver (`mysql-connector-java`) to interact with Doris. This is possible due to Apache Doris's high compatibility with the MySQL protocol. Ensure that this driver is available in the classpath, which is typically handled by the plugin's Maven dependencies.

---
*This documentation is for the Doris storage plugin currently under development.*
