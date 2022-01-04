# V9 upgrade
Starting from v9, SkyWalking introduces the new core concept [**Layer**]().
A layer represents an abstract framework in computer science, such as Operating System(OS_LINUX layer),
Kubernetes(k8s layer). This kind of layer would-be owners of different services/instances detected from different technology.
The query-protocol [metadata-v2](https://github.com/apache/skywalking-query-protocol/blob/master/metadata-v2.graphqls) has been used.
The compatibility with previous releases is as below.

## Compatibility from previous version 
1. The query-protocol [metadata-v1](https://github.com/apache/skywalking-query-protocol/blob/master/metadata.graphqls) is provided on the top of the v2 implementation.
   All query-protocol could be compatible with previous query.
2. MAL: [metric level function](../../../docs/en/concepts-and-designs/mal.md) add argument `Layer`(require). Previous MAL expressions with this function should add this argument.
3. LAL: [Extractor](../../../docs/en/concepts-and-designs/lal.md) add function `layer`. If don't set it manual, the default layer is `GENERAL` and the logs from `ALS` the
   default layer is `mesh`.
4. Storage：Table `ServiceTraffic` add columns `service_id`， `short_name` and `layer`，table `InstanceTraffic` add columns `layer`.
   These data would be incompatible with previous in some storage, including H2/OpenSearch/MySQL/TiDB/InfluxDB/PostgreSQL.
   Make sure to remove the older table `ServiceTraffic` and `InstanceTraffic` before OAP(v9) starts. 
   OAP would generate the new table in the start procedure and generate new data when traffic come.
5. All other metrics are compatible with previous data format.
