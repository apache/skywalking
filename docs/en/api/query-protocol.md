# Query Protocol
Query Protocol defines a set of APIs in GraphQL grammar to provide data query and interactive capabilities with SkyWalking
native visualization tool or 3rd party system, including Web UI, CLI or private system.

Query protocol official repository, https://github.com/apache/skywalking-query-protocol.

All deprecated APIs are moved [here](./query-protocol-deprecated.md).

### Metadata  
Metadata contains concise information on all services and their instances, endpoints, etc. under monitoring.
You may query the metadata in different ways.
```graphql
extend type Query {
    # Normal service related meta info 
    getAllServices(duration: Duration!, group: String): [Service!]!
    searchServices(duration: Duration!, keyword: String!): [Service!]!
    searchService(serviceCode: String!): Service

    # Fetch all services of Browser type
    getAllBrowserServices(duration: Duration!): [Service!]!
    searchBrowserServices(duration: Duration!, keyword: String!): [Service!]!
    searchBrowserService(serviceCode: String!): Service

    # Service instance query
    getServiceInstances(duration: Duration!, serviceId: ID!): [ServiceInstance!]!

    # Endpoint query
    # Consider there are huge numbers of endpoint,
    # must use endpoint owner's service id, keyword and limit filter to do query.
    searchEndpoint(keyword: String!, serviceId: ID!, limit: Int!): [Endpoint!]!
    getEndpointInfo(endpointId: ID!): EndpointInfo

    # Process query
    # Read process list.
    listProcesses(duration: Duration!, instanceId: ID!): [Process!]!
    # Find process according to given ID. Return null if not existing.
    getProcess(processId: ID!): Process
    # Get the number of matched processes through serviceId, labels
    # Labels: the matched process should contain all labels
    #
    # The return is not a precise number, the process has its lifecycle, as it reboots and shutdowns with time.
    # The return number just gives an abstract of the scale of profiling that would be applied.
    estimateProcessScale(serviceId: ID!, labels: [String!]!): Long!

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

#### V3 APIs
Provide Metrics V3 query APIs since 9.5.0, including metadata and MQE.
SkyWalking Metrics Query Expression(MQE) is an extension query mechanism. MQE allows users to do simple query-stage calculation like well known PromQL
through GraphQL. The expression's syntax can refer to [here](./metrics-query-expression.md).

```graphql
extend type Query {
    # Metrics definition metadata query. Response the metrics type which determines the suitable query methods.
    typeOfMetrics(name: String!): MetricsType!
    # Get the list of all available metrics in the current OAP server.
    # Param, regex, could be used to filter the metrics by name.
    listMetrics(regex: String): [MetricDefinition!]!
    execExpression(expression: String!, entity: Entity!, duration: Duration!): ExpressionResult!
}
```

```graphql
type ExpressionResult {
    type: ExpressionResultType!
    # When the type == TIME_SERIES_VALUES, the results would be a collection of MQEValues.
    # In other legal type cases, only one MQEValues is expected in the array.
    results: [MQEValues!]!
    # When type == ExpressionResultType.UNKNOWN,
    # the error message includes the expression resolving errors.
    error: String
}
```

```graphql
enum ExpressionResultType {
    # Can't resolve the type of the given expression.
    UNKNOWN
    # A single value
    SINGLE_VALUE
    # A collection of time-series values.
    # The value could have labels or not.
    TIME_SERIES_VALUES
    # A collection of aggregated values through metric sort function
    SORTED_LIST
    # A collection of sampled records.
    # When the original metric type is sampled records
    RECORD_LIST
}
```

### Logs
```graphql
extend type Query {
    # Return true if the current storage implementation supports fuzzy query for logs.
    supportQueryLogsByKeywords: Boolean!
    queryLogs(condition: LogQueryCondition): Logs

    # Test the logs and get the results of the LAL output.
    test(requests: LogTestRequest!): LogTestResponse!
}
```

Log implementations vary between different database options. Some search engines like ElasticSearch and OpenSearch can support
full log text fuzzy queries, while others do not due to considerations related to performance impact and end user experience.

`test` API serves as the debugging tool for native LAL parsing. 

### Trace
```graphql
extend type Query {
    queryBasicTraces(condition: TraceQueryCondition): TraceBrief
    queryTrace(traceId: ID!): Trace
}
```

Trace query fetches trace segment lists and spans of given trace IDs.

### Alarm
```graphql
extend type Query {
    getAlarmTrend(duration: Duration!): AlarmTrend!
    getAlarm(duration: Duration!, scope: Scope, keyword: String, paging: Pagination!, tags: [AlarmTag]): Alarms
}
```

Alarm query identifies alarms and related events.

### Event
```graphql
extend type Query {
    queryEvents(condition: EventQueryCondition): Events
}
```

Event query fetches the event list based on given sources and time range conditions.

### Profiling
SkyWalking offers two types of [profiling](../concepts-and-designs/profiling.md), in-process and out-process, allowing users to create tasks and check their execution status.

#### In-process profiling

```graphql
extend type Mutation {
    # crate new profile task
    createProfileTask(creationRequest: ProfileTaskCreationRequest): ProfileTaskCreationResult!
}
extend type Query {
    # query all task list, order by ProfileTask#startTime descending
    getProfileTaskList(serviceId: ID, endpointName: String): [ProfileTask!]!
    # query all task logs
    getProfileTaskLogs(taskID: String): [ProfileTaskLog!]!
    # query all task profiled segment list
    getProfileTaskSegmentList(taskID: String): [BasicTrace!]!
    # query profiled segment
    getProfiledSegment(segmentId: String): ProfiledSegment
    # analyze profiled segment, start and end time use timestamp(millisecond)
    getProfileAnalyze(segmentId: String!, timeRanges: [ProfileAnalyzeTimeRange!]!): ProfileAnalyzation!
}
```

#### Out-process profiling

```graphql
extend type Mutation {
    # create a new eBPF fixed time profiling task
    createEBPFProfilingFixedTimeTask(request: EBPFProfilingTaskFixedTimeCreationRequest!): EBPFProfilingTaskCreationResult!

    # create a new eBPF network profiling task
    createEBPFNetworkProfiling(request: EBPFProfilingNetworkTaskRequest!): EBPFProfilingTaskCreationResult!
    # keep alive the eBPF profiling task
    keepEBPFNetworkProfiling(taskId: ID!): EBPFNetworkKeepProfilingResult!
}
extend type Query {
    # query eBPF profiling data for prepare create task
    queryPrepareCreateEBPFProfilingTaskData(serviceId: ID!): EBPFProfilingTaskPrepare!
    # query eBPF profiling task list
    queryEBPFProfilingTasks(serviceId: ID, serviceInstanceId: ID, targets: [EBPFProfilingTargetType!]): [EBPFProfilingTask!]!
    # query schedules from profiling task
    queryEBPFProfilingSchedules(taskId: ID!): [EBPFProfilingSchedule!]!
    # analyze the profiling schedule
    # aggregateType is "EBPFProfilingAnalyzeAggregateType#COUNT" as default. 
    analysisEBPFProfilingResult(scheduleIdList: [ID!]!, timeRanges: [EBPFProfilingAnalyzeTimeRange!]!, aggregateType: EBPFProfilingAnalyzeAggregateType): EBPFProfilingAnalyzation!
}
```

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
