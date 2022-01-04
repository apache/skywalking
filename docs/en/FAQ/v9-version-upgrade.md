# V9 upgrade
Starting from v9, SkyWalking introduces the new core concept [**Layer**]().
A layer represents an abstract framework in computer science, such as Operating System(OS_LINUX layer),
Kubernetes(k8s layer). This kind of layer would-be owners of different services/instances detected from different technology.
The query-protocol [metadata-v2](https://github.com/apache/skywalking-query-protocol/blob/master/metadata-v2.graphqls) has been used.
The compatibility with previous releases is as below.

## Compatibility from previous version 
1. The query-protocol [metadata-v1](https://github.com/apache/skywalking-query-protocol/blob/master/metadata.graphqls) is provided on the top of the v2 implementation.
   All query-protocol could be compatible with previous query.
2. MAL: [metric level function](../../../docs/en/concepts-and-designs/mal.md) add an required argument `Layer`. Previous MAL expressions should add this argument.
3. LAL: [Extractor](../../../docs/en/concepts-and-designs/lal.md) add function `layer`. If don't set it manual, the default layer is `GENERAL` and the logs from `ALS` the
   default layer is `mesh`.
4. Storage：add `service_id`， `short_name` and `layer` columns to table `ServiceTraffic`， add `layer` column to table `InstanceTraffic`.
   These data would be incompatible with previous in some storage, including H2/OpenSearch/MySQL/TiDB/InfluxDB/PostgreSQL.
   Make sure to remove the older `ServiceTraffic` and `InstanceTraffic` tables before OAP(v9) starts. 
   OAP would generate the new table in the start procedure, and recreate all existing services and instances when traffic comes.
5. All other metrics are compatible with the previous data format, so you wouldn't lose metrics.
