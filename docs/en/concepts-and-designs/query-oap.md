# Query in OAP
Query(s) are provided in GraphQL format. All GraphQL definition files are [here](../../../oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol).

Here are the explanation of these definitions.

## Common Objects
All objects defined in `common.graphqls` are simple common objects, which could be used in any other 
`*.graphqls` definition files. Such as, **Duration**, **Step**, **Scope**.

## Metadata
Through Metadata query(s) which defined in `metadata.graphqls`, you could have the meta info of Service, Service Instance and Endpoint, 
including name, id, relationship. 

## Metric
Metric query(s) in `metric.graphqls` could be used to fetch data from any variable defined in **OAL** scripts. 
You could read value or linear trend of the metric variable by the given duration and id.

Also, Thermodynamic heatmap is very different with other single value metric, so it is a special
query op. **Thermodynamic** object, a data matrix, will be returned to represent.

## Aggregation Query
Aggregation query(s) in `aggregation.graphs` right now, are most TopN related query(s). You could 
get **TopN** service, service instance and endpoint in different ways.

## Topology Query
Topology query(s) in `topology.graphqls` provide the consistency query no matter what sources do you 
get the topology relation. Also, all entity IDs included in topology will be returned too, for your 
convenience to do metric query(s).

## Trace
At beginning and some scenarios, SkyWalking will be considered as a distributed tracing system. So
of course we will provide trace query. In `trace.graphql` you will find the format, it is nearly the 
same format of our trace report/uplink protocol, just in GraphQL version. 

## Alarm
Alarm query(s) in `alarm.graphql` could be get triggered alarms. Although we believe alarm webhook
in alarm settings(see [alarm setting doc](../setup/backend/backend-alarm.md)) will be more useful
and powerful, still we provide query for SkyWalking UI or simple use scenarios.
