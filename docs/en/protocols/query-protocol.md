# Query Protocol
Query Protocol defines a set of APIs in GraphQL grammar to provide data query and interactive capabilities with SkyWalking
native visualization tool or 3rd party system, including Web UI, CLI or private system.

Query protocol official repository, https://github.com/apache/skywalking-query-protocol.

### Metadata  
Metadata contains concise information on all services and their instances, endpoints, etc. under monitoring.
You may query the metadata in different ways.
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
The topology and dependency graphs among services, instances and endpoints. Includes direct relationships or global maps.

```graphql
extend type Query {
    # Query the global topology
    getGlobalTopology(duration: Duration!): Topology
    # Query the topology, based on the given service
    getServiceTopology(serviceId: ID!, duration: Duration!): Topology
    # Query the topology, based on the given services.
    # `#getServiceTopology` could be replaced by this.
    getServicesTopology(serviceIds: [ID!]!, duration: Duration!): Topology
    # Query the instance topology, based on the given clientServiceId and serverServiceId
    getServiceInstanceTopology(clientServiceId: ID!, serverServiceId: ID!, duration: Duration!): ServiceInstanceTopology
    # Query the topology, based on the given endpoint
    getEndpointTopology(endpointId: ID!, duration: Duration!): Topology
    # v2 of getEndpointTopology
    getEndpointDependencies(endpointId: ID!, duration: Duration!): EndpointTopology
}
```

### Metrics
Metrics query targets all objects defined in [OAL script](../concepts-and-designs/oal.md) and [MAL](../concepts-and-designs/mal.md). 
You may obtain the metrics data in linear or thermodynamic matrix formats based on the aggregation functions in script. 

#### V2 APIs
Provide Metrics V2 query APIs since 8.0.0, including metadata, single/multiple values, heatmap, and sampled records metrics.
```graphql
extend type Query {
    # Metrics definition metadata query. Response the metrics type which determines the suitable query methods.
    typeOfMetrics(name: String!): MetricsType!
    # Get the list of all available metrics in the current OAP server.
    # Param, regex, could be used to filter the metrics by name.
    listMetrics(regex: String): [MetricDefinition!]!

    # Read metrics single value in the duration of required metrics
    readMetricsValue(condition: MetricsCondition!, duration: Duration!): Long!
    # Read time-series values in the duration of required metrics
    readMetricsValues(condition: MetricsCondition!, duration: Duration!): MetricsValues!
    # Read entity list of required metrics and parent entity type.
    sortMetrics(condition: TopNCondition!, duration: Duration!): [SelectedRecord!]!
    # Read value in the given time duration, usually as a linear.
    # labels: the labels you need to query.
    readLabeledMetricsValues(condition: MetricsCondition!, labels: [String!]!, duration: Duration!): [MetricsValues!]!
    # Heatmap is bucket based value statistic result.
    readHeatMap(condition: MetricsCondition!, duration: Duration!): HeatMap
    # Read the sampled records
    # TopNCondition#scope is not required.
    readSampledRecords(condition: TopNCondition!, duration: Duration!): [SelectedRecord!]!
}
```

#### V1 APIs
3 types of metrics can be queried. V1 APIs were introduced since 6.x. Now they are a shell to V2 APIs.
1. Single value. Most default metrics are in single value. `getValues` and `getLinearIntValues` are suitable for this purpose.
1. Multiple value.  A metric defined in OAL includes multiple value calculations. Use `getMultipleLinearIntValues` to obtain all values. `percentile` is a typical multiple value function in OAL.
1. Heatmap value. Read [Heatmap in WIKI](https://en.wikipedia.org/wiki/Heat_map) for details. `thermodynamic` is the only OAL function. Use `getThermodynamic` to get the values.
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
Aggregation query means that the metrics data need a secondary aggregation at query stage, which causes the query 
interfaces to have some different arguments. A typical example of aggregation query is the `TopN` list of services. 
Metrics stream aggregation simply calculates the metrics values of each service, but the expected list requires ordering metrics data
by their values.

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
The following queries are for specific features, including trace, alarm, and profile.
1. Trace. Query distributed traces by this.
1. Alarm. Through alarm query, you can find alarm trends and their details.

The actual query GraphQL scripts can be found in the `query-protocol` folder [here](../../../oap-server/server-query-plugin/query-graphql-plugin/src/main/resources).

## Condition
### Duration
Duration is a widely used parameter type as the APM data is time-related. See the following for more details. 
Step relates to precision. 
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
