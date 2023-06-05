# Deprecated Query Protocol
The following query services are deprecated since 9.5.0. All these queries are still available for the short term to keep compatibility. 

Query protocol official repository, https://github.com/apache/skywalking-query-protocol.

### Metrics
Metrics query targets all objects defined in [OAL script](../concepts-and-designs/oal.md) and [MAL](../concepts-and-designs/mal.md). 
You may obtain the metrics data in linear or thermodynamic matrix formats based on the aggregation functions in script.

#### V2 APIs
Provide Metrics V2 query APIs since 8.0.0, including metadata, single/multiple values, heatmap, and sampled records metrics.
```graphql
extend type Query {
    # Read metrics single value in the duration of required metrics
    readMetricsValue(condition: MetricsCondition!, duration: Duration!): Long!
    # Read metrics single value in the duration of required metrics
    # NullableValue#isEmptyValue == true indicates no telemetry data rather than aggregated value is actually zero.
    readNullableMetricsValue(condition: MetricsCondition!, duration: Duration!): NullableValue!
    # Read time-series values in the duration of required metrics
    readMetricsValues(condition: MetricsCondition!, duration: Duration!): MetricsValues!
    # Read entity list of required metrics and parent entity type.
    sortMetrics(condition: TopNCondition!, duration: Duration!): [SelectedRecord!]!
    # Read value in the given time duration, usually as a linear.
    # labels: the labels you need to query.
    readLabeledMetricsValues(condition: MetricsCondition!, labels: [String!]!, duration: Duration!): [MetricsValues!]!
    # Heatmap is bucket based value statistic result.
    readHeatMap(condition: MetricsCondition!, duration: Duration!): HeatMap
    # Deprecated since 9.3.0, replaced by readRecords defined in record.graphqls
    # Read the sampled records
    # TopNCondition#scope is not required.
    readSampledRecords(condition: TopNCondition!, duration: Duration!): [SelectedRecord!]!
}
```

#### V1 APIs
3 types of metrics can be queried. V1 APIs were introduced since 6.x. Now they are a shell to V2 APIs.
1. Single value. Most default metrics are in single value. `getValues` and `getLinearIntValues` are suitable for this purpose.
2. Multiple value.  A metric defined in OAL includes multiple value calculations. Use `getMultipleLinearIntValues` to obtain all values. `percentile` is a typical multiple value function in OAL.
3. Heatmap value. Read [Heatmap in WIKI](https://en.wikipedia.org/wiki/Heat_map) for details. `thermodynamic` is the only OAL function. Use `getThermodynamic` to get the values.
```graphql
extend type Query {
    getValues(metric: BatchMetricConditions!, duration: Duration!): IntValues
    getLinearIntValues(metric: MetricCondition!, duration: Duration!): IntValues
    # Query the type of metrics including multiple values, and format them as multiple lines.
    # The seq of these multiple lines base on the calculation func in OAL
    # Such as, should us this to query the result of func percentile(50,75,90,95,99) in OAL,
    # then five lines will be responded, p50 is the first element of return value.
    getMultipleLinearIntValues(metric: MetricCondition!, numOfLinear: Int!, duration: Duration!): [IntValues!]!
    getThermodynamic(metric: MetricCondition!, duration: Duration!): Thermodynamic
}
```

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

### Record
Record is a general and abstract type for collected raw data.
In the observability, traces and logs have specific and well-defined meanings, meanwhile, the general records represent other
collected records. Such as sampled slow SQL statement, HTTP request raw data(request/response header/body)

```graphql
extend type Query {
    # Query collected records with given metric name and parent entity conditions, and return in the requested order.
    readRecords(condition: RecordCondition!, duration: Duration!): [Record!]!
}
```
