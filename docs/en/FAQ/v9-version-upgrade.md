# V9 upgrade
Starting from v9, SkyWalking introduces the new core concept [**Layer**](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java).
A layer represents an abstract framework in computer science, such as Operating System(OS_LINUX layer),
Kubernetes(k8s layer). This kind of layer would be catalogs on the new [booster UI](https://github.com/apache/skywalking-booster-ui) of various services/instances detected by different technologies.
The query-protocol [metadata-v2](https://github.com/apache/skywalking-query-protocol/blob/master/metadata-v2.graphqls) has been used.
The compatibility with previous releases is as below.

## Query compatibility from previous version 
1. The query-protocol [metadata-v1](https://github.com/apache/skywalking-query-protocol/blob/master/metadata.graphqls) is provided on the top of the v2 implementation.
2. All metrics are compatible with the previous data format, so you wouldn't lose metrics.

Notice **Incompatibility (1)**, the UI template configuration protocol is incompatible.

## Incompatibility
1. The [UI configuration protocol](https://github.com/apache/skywalking-query-protocol/blob/master/ui-configuration.graphqls) has been changed by following the design of new [booster UI](https://github.com/apache/skywalking-booster-ui). So, the RocketBot UI can't work with the v9 backend. You need to remove `ui_template` index/template/table in your chosen storage, and reboot OAP in `default` or `init` mode.
2. MAL: [metric level function](../../../docs/en/concepts-and-designs/mal.md) add an required argument `Layer`. Previous MAL expressions should add this argument.
3. LAL: [Extractor](../../../docs/en/concepts-and-designs/lal.md) add function `layer`. If don't set it manual, the default layer is `GENERAL` and the logs from `ALS` the
   default layer is `mesh`.
4. Storage：Add `service_id`， `short_name` and `layer` columns to table `ServiceTraffic`.
   These data would be incompatible with previous versions.
   Make sure to remove the older `ServiceTraffic` table before OAP(v9) starts. 
   OAP would generate the new table in the start procedure, and recreate all existing services when traffic comes.
   Since V9.1, SQL Database: move `Tags list` from `Segment`, `Logs`, `Alarms` to their additional tables, remove them before OAP starts.
5. UI-template: Re-design for V9. Make sure to remove the older `ui_template` table before OAP(v9) starts.
