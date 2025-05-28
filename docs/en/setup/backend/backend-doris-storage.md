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

Please refer to the **[Doris Table Initialization Script](placeholder-link-to-doris-schema.sql)** for the required SQL DDL statements. *(Self-note: This link is a placeholder and needs to be updated once the actual script is available.)*

Failure to create the tables beforehand will result in errors during SkyWalking startup.

## Development Note

The SkyWalking Doris storage plugin utilizes the MySQL JDBC driver (`mysql-connector-java`) to interact with Doris. This is possible due to Apache Doris's high compatibility with the MySQL protocol. Ensure that this driver is available in the classpath, which is typically handled by the plugin's Maven dependencies.

---
*This documentation is for the Doris storage plugin currently under development.*
