# Query Protocol
Query Protocol defines a set of APIs in GraphQL grammar to provide data query and interactive capabilities with SkyWalking
native visualization tool or 3rd party system, including Web UI, CLI or private system.

Query protocol official repository, https://github.com/apache/skywalking-query-protocol.

### Metadata  
Metadata includes the brief info of the whole under monitoring services and their instances, endpoints, etc.
Use multiple ways to query this meta data.
```graphql
extend type Query {
    getGlobalBrief(duration: Duration!): ClusterBrief

    # Normal service related metainfo 
    getAllServices(duration: Duration!): [Service!]!
    searchServices(duration: Duration!, keyword: String!): [Service!]!
    searchService(serviceCode: String!): Service
    
    # Fetch all services of Browser type
    getAllBrowserServices(duration: Duration!): [Service!]!

    # Service intance query
    getServiceInstances(duration: Duration!, serviceId: ID!): [ServiceInstance!]!

    # Endpoint query
    # Consider there are huge numbers of endpoint,
    # must use endpoint owner's service id, keyword and limit filter to do query.
    searchEndpoint(keyword: String!, serviceId: ID!, limit: Int!): [Endpoint!]!
    getEndpointInfo(endpointId: ID!): EndpointInfo

    # Database related meta info.
    getAllDatabases(duration: Duration!): [Database!]!
    getTimeInfo: TimeInfo
}
```

### Topology
Show the topology and dependency graph of services or endpoints. Including direct relationship or global map.

```graphql
extend type Query {
    # Query the global topology
    getGlobalTopology(duration: Duration!): Topology
    # Query the topology, based on the given service
    getServiceTopology(serviceId: ID!, duration: Duration!): Topology
    # Query the instance topology, based on the given clientServiceId and serverServiceId
    getServiceInstanceTopology(clientServiceId: ID!, serverServiceId: ID!, duration: Duration!): ServiceInstanceTopology
    # Query the topology, based on the given endpoint
    getEndpointTopology(endpointId: ID!, duration: Duration!): Topology
}
```

### Metrics
Metrics query targets all the objects defined in [OAL script](../concepts-and-designs/oal.md). You could get the 
metrics data in linear or thermodynamic matrix formats based on the aggregation functions in script. 

3 types of metrics could be query
1. Single value. The type of most default metrics is single value, consider this as default. `getValues` and `getLinearIntValues` are suitable for this.
1. Multiple value.  One metrics defined in OAL include multiple value calculations. Use `getMultipleLinearIntValues` to get all values. `percentile` is a typical multiple value func in OAL.
1. Heatmap value. Read [Heatmap in WIKI](https://en.wikipedia.org/wiki/Heat_map) for detail. `thermodynamic` is the only OAL func. Use `getThermodynamic` to get the values.
```graphql
extend type Query {
    getValues(metric: BatchMetricConditions!, duration: Duration!): IntValues
    getLinearIntValues(metric: MetricCondition!, duration: Duration!): IntValues
    # Query the type of metrics including multiple values, and format them as multiple linears.
    # The seq of these multiple lines base on the calculation func in OAL
    # Such as, should us this to query the result of func percentile(50,75,90,95,99) in OAL,
    # then five lines will be responsed, p50 is the first element of return value.
    getMultipleLinearIntValues(metric: MetricCondition!, numOfLinear: Int!, duration: Duration!): [IntValues!]!
    getThermodynamic(metric: MetricCondition!, duration: Duration!): Thermodynamic
}
```

Metrics are defined in the `config/oal/*.oal` files.

### Aggregation
Aggregation query means the metrics data need a secondary aggregation in query stage, which makes the query 
interfaces have some different arguments. Such as, `TopN` list of services is a very typical aggregation query, 
metrics stream aggregation just calculates the metrics values of each service, but the expected list needs ordering metrics data
by the values.

Aggregation query is for single value metrics only.

```graphql
# The aggregation query is different with the metric query.
# All aggregation queries require backend or/and storage do aggregation in query time.
extend type Query {
    # TopN is an aggregation query.
    getServiceTopN(name: String!, topN: Int!, duration: Duration!, order: Order!): [TopNEntity!]!
    getAllServiceInstanceTopN(name: String!, topN: Int!, duration: Duration!, order: Order!): [TopNEntity!]!
    getServiceInstanceTopN(serviceId: ID!, name: String!, topN: Int!, duration: Duration!, order: Order!): [TopNEntity!]!
    getAllEndpointTopN(name: String!, topN: Int!, duration: Duration!, order: Order!): [TopNEntity!]!
    getEndpointTopN(serviceId: ID!, name: String!, topN: Int!, duration: Duration!, order: Order!): [TopNEntity!]!
}
```

### Others
The following query(s) are for specific features, including trace, alarm or profile.
1. Trace. Query distributed traces by this.
1. Alarm. Through alarm query, you can have alarm trend and details.

The actual query GraphQL scrips could be found inside `query-protocol` folder in [here](../../../oap-server/server-query-plugin/query-graphql-plugin/src/main/resources).

## Condition
### Duration
Duration is a widely used parameter type as the APM data is time related. The explanations are as following. 
Step is related the precision. 
```graphql
# The Duration defines the start and end time for each query operation.
# Fields: `start` and `end`
#   represents the time span. And each of them matches the step.
#   ref https://www.ietf.org/rfc/rfc3339.txt
#   The time formats are
#       `SECOND` step: yyyy-MM-dd HHmmss
#       `MINUTE` step: yyyy-MM-dd HHmm
#       `HOUR` step: yyyy-MM-dd HH
#       `DAY` step: yyyy-MM-dd
#       `MONTH` step: yyyy-MM
# Field: `step`
#   represents the accurate time point.
# e.g.
#   if step==HOUR , start=2017-11-08 09, end=2017-11-08 19
#   then
#       metrics from the following time points expected
#       2017-11-08 9:00 -> 2017-11-08 19:00
#       there are 11 time points (hours) in the time span.
input Duration {
    start: String!
    end: String!
    step: Step!
}

enum Step {
    MONTH
    DAY
    HOUR
    MINUTE
    SECOND
}
```
