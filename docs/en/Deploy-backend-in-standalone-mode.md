# Usage scenario（no provide in alpha version，planning provide in beta version）
Default standalong mode collector means don't support cluster. It uses H2 as storage layer implementation, suggest that use only for preview, test, demonstration, low throughputs and small scale system.

If you are using SkyWalking in a low throughputs monitoring scenario, and don't want to deploy cluster, at least, switch the storage implementation from H2 to Elasticsearch.

**H2 storage implementation is not provided in 5.0.0-alpha, so you must deploy ElasticSearch before try to start backend**

## Requirements
* JDK 8+

## Download
* [Releases](https://github.com/apache/incubator-skywalking/releases)

## Quick start
You can simplely tar/unzip and startup if ports 8080, 10800, 11800, 12800 are free.

- `tar -xvf skywalking-dist.tar.gz` in Linux, or unzip in windows.
- run `bin/startup.sh` or `bin/startup.bat`

You should keep the `config/application.yml` as default.

- NOTICE: **In 5.0.0-alpha, startup.sh will start two processes, collector and UI, and UI uses 127.0.0.1:10800 as default.**

## Use Elastic Search instead of H2 as storage layer implementation
Even in standalone mode, collector can run with Elastic Search as storage. If so, uncomment the `storage` section in `application.yml`, set the config right. The default configs fit for collector and Elasticsearch both running in same machine, and not cluster.

## Deploy Elasticsearch server
- Modify `elasticsearch.yml`
  - Set `cluster.name: CollectorDBCluster`
  - Set `node.name: anyname`, this name can be any, it based on Elasticsearch.
  - Add the following configurations

```
# The ip used for listening
network.host: 0.0.0.0
thread_pool.bulk.queue_size: 1000
```

- Start Elasticsearch
