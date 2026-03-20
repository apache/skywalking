# GraphQL & MQE Expected File Patterns

GraphQL queries use `swctl` CLI tool with `--display yaml` output. MQE (Metrics Query Engine) queries also go through the GraphQL endpoint.

**Port:** `${oap_12800}` — Base URL: `http://${oap_host}:${oap_12800}/graphql`

## GraphQL Schema Definitions

All GraphQL types, queries, and inputs are defined in `.graphqls` files at:
```
oap-server/server-query-plugin/query-graphql-plugin/src/main/resources/query-protocol/
```

| Schema File | Key Types & Queries |
|-------------|-------------------|
| `common.graphqls` | `Duration`, `Pagination`, `Scope`, `DetectPoint`, `KeyValue`, `DebuggingTrace`, `HealthStatus` |
| `metadata-v2.graphqls` | `Service`(id,name,group,shortName,layers,normal), `ServiceInstance`(id,name,attributes,language,instanceUUID), `Endpoint`(id,name), `Process` — queries: `listLayers`, `listServices`, `listInstances`, `findEndpoint` |
| `topology.graphqls` | `Topology`(nodes,calls), `Node`(id,name,type,isReal,layers), `Call`(source,target,id,detectPoints) — queries: `getGlobalTopology`, `getServiceTopology`, `getServicesTopology`, `getServiceInstanceTopology`, `getEndpointDependencies`, `getProcessTopology` |
| `trace.graphqls` | `Span`(traceId,segmentId,spanId,parentSpanId,refs,serviceCode,serviceInstanceName,startTime,endTime,endpointName,type,peer,component,isError,layer,tags,logs,attachedEvents), `TraceBrief`, `BasicTrace` — queries: `queryBasicTraces`, `queryTrace` |
| `trace-v2.graphqls` | `TraceList`(traces,retrievedTimeRange,debuggingTrace), `TraceV2`(spans), `RetrievedTimeRange`(startTime,endTime) — query: `queryTraces` |
| `metrics-v3.graphqls` | `ExpressionResult`(type,results,error,debuggingTrace), `MQEValues`(metric,values), `MQEValue`(id,owner,value,traceID), `ExpressionResultType`(UNKNOWN,SINGLE_VALUE,TIME_SERIES_VALUES,SORTED_LIST,RECORD_LIST), `Entity` — query: `execExpression` |
| `alarm.graphqls` | `AlarmMessage`(startTime,scope,id,name,message,events,tags,snapshot), `AlarmSnapshot`(expression,metrics) — queries: `getAlarm`, `queryAlarmTagAutocompleteKeys`, `queryAlarmTagAutocompleteValues` |
| `log.graphqls` | `Log`(serviceName,serviceInstanceName,endpointName,traceId,timestamp,contentType,content,tags), `Logs`(errorReason,logs) — queries: `queryLogs`, `test` (LAL test) |
| `event.graphqls` | `Event`(uuid,source,name,type,message,parameters,startTime,endTime,layer), `Source`(service,serviceInstance,endpoint) — query: `queryEvents` |
| `hierarchy.graphqls` | `ServiceHierarchy`, `InstanceHierarchy`, `HierarchyServiceRelation`, `LayerLevel` — queries: `getServiceHierarchy`, `getInstanceHierarchy`, `listLayerLevels` |
| `profile.graphqls` | Profile task creation/query |
| `ebpf-profiling.graphqls` | eBPF profiling task creation/query |
| `continuous-profiling.graphqls` | Continuous profiling policy/task management |
| `async-profiler.graphqls` | Async profiler task management |
| `pprof.graphqls` | pprof profiling task management |
| `browser-log.graphqls` | Browser error log query |
| `record.graphqls` | Sampled record reading |
| `top-n-records.graphqls` | Top-N record query |
| `ondemand-pod-log.graphqls` | On-demand pod log query |
| `ui-configuration.graphqls` | UI dashboard template management |
| `metric.graphqls` | Legacy metric query (deprecated, use metrics-v3) |
| `metrics-v2.graphqls` | Metrics v2 query (deprecated, use metrics-v3) |
| `aggregation.graphqls` | Legacy aggregation query (deprecated) |
| `metadata.graphqls` | Legacy metadata query (deprecated, use metadata-v2) |

## Service List

**GraphQL schema:** `metadata-v2.graphqls` — `listServices(layer: String): [Service!]!`

**Service type fields:** `id`, `name`, `group`, `shortName`, `layers`, `normal`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql service ls
```

**Expected:**
```yaml
{{- contains . }}
- id: {{ b64enc "e2e-service-provider" }}.1
  name: e2e-service-provider
  group: ""
  shortname: e2e-service-provider
  normal: true
  layers:
    - GENERAL
- id: {{ b64enc "e2e-service-consumer" }}.1
  name: e2e-service-consumer
  group: ""
  shortname: e2e-service-consumer
  normal: true
  layers:
    - GENERAL
{{- end }}
```

## Layer List

**GraphQL schema:** `metadata-v2.graphqls` — `listLayers: [String!]!`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql layer ls
```

**Expected:**
```yaml
{{- contains . }}
- GENERAL
{{- end }}
```

## Service Instance List

**GraphQL schema:** `metadata-v2.graphqls` — `listInstances(duration, serviceId): [ServiceInstance!]!`

**ServiceInstance type fields:** `id`, `name`, `attributes`(`name`,`value`), `language`, `instanceUUID`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql instance list \
  --service-name=e2e-service-provider
```

**Expected:**
```yaml
{{- contains . }}
- id: {{ b64enc "e2e-service-provider" }}.1_{{ b64enc "provider1" }}
  name: provider1
  language: JAVA
  instanceuuid: {{ notEmpty .instanceuuid }}
  attributes:
    {{- contains .attributes }}
    - name: {{ notEmpty .name }}
      value: {{ notEmpty .value }}
    {{- end }}
{{- end }}
```

## Endpoint List

**GraphQL schema:** `metadata-v2.graphqls` — `findEndpoint(keyword, serviceId, limit, duration): [Endpoint!]!`

**Endpoint type fields:** `id`, `name`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql endpoint list \
  --service-name=e2e-service-provider
```

**Expected:**
```yaml
{{- contains . }}
- id: {{ b64enc "e2e-service-provider" }}.1_{{ b64enc "POST:/users" }}
  name: POST:/users
{{- end }}
```

## Service Dependency (Topology)

**GraphQL schema:** `topology.graphqls` — `getServiceTopology(serviceId, duration): Topology`

**Topology type:** `nodes: [Node!]!` + `calls: [Call!]!` + `debuggingTrace`
- **Node fields:** `id`, `name`, `type`, `isReal`, `layers`
- **Call fields:** `source`, `sourceComponents`, `target`, `targetComponents`, `id`, `detectPoints`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql dependency service \
  --service-name=e2e-service-provider
```

**Expected:**
```yaml
debuggingtrace: null
nodes:
{{- contains .nodes }}
- id: {{ b64enc "e2e-service-provider" }}.1
  name: e2e-service-provider
  type: Tomcat
  isreal: true
  layers:
    - GENERAL
- id: {{ b64enc "localhost:-1" }}.0
  name: localhost:-1
  type: H2
  isreal: false
  layers:
    - VIRTUAL_DATABASE
{{- end }}
calls:
{{- contains .calls }}
- source: {{ b64enc "e2e-service-provider" }}.1
  sourcecomponents:
    - h2-jdbc-driver
  target: {{ b64enc "localhost:-1" }}.0
  targetcomponents: []
  id: {{ b64enc "e2e-service-provider" }}.1-{{ b64enc "localhost:-1" }}.0
  detectpoints:
    - CLIENT
{{- end }}
```

## Trace List (v2)

**GraphQL schema:** `trace-v2.graphqls` — `queryTraces(condition, debug): TraceList`

**TraceList type:** `traces: [TraceV2!]!` + `retrievedTimeRange`(`startTime`, `endTime`) + `debuggingTrace`
- **TraceV2:** `spans: [Span!]!`
- **Span fields** (from `trace.graphqls`): `traceId`, `segmentId`, `spanId`, `parentSpanId`, `refs`, `serviceCode`, `serviceInstanceName`, `startTime`(ms), `endTime`(ms), `endpointName`, `type`(Local/Entry/Exit), `peer`, `component`, `isError`, `layer`(Unknown/Database/RPCFramework/Http/MQ/Cache), `tags`(`[KeyValue]`), `logs`(`[LogEntity]`), `attachedEvents`(`[SpanAttachedEvent]`)

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql tv2 ls
```

**Expected:**
```yaml
debuggingtrace: null
retrievedtimerange:
  {{- with .retrievedtimerange }}
  starttime: {{ gt .starttime 0 }}
  endtime: {{ gt .endtime 0 }}
  {{- end }}
traces:
  {{- contains .traces }}
  - spans:
      {{- contains .spans }}
      - traceid: {{ .traceid }}
        segmentid: {{ .segmentid }}
        spanid: 0
        parentspanid: -1
        refs: []
        servicecode: e2e-service-consumer
        serviceinstancename: consumer1
        starttime: {{ gt .starttime 0 }}
        endtime: {{ gt .endtime 0 }}
        endpointname: POST:/users
        type: Entry
        peer: ""
        component: Tomcat
        iserror: false
        layer: Http
        tags:
          {{- contains .tags }}
          - key: url
            value: {{ notEmpty .value }}
          - key: http.method
            value: POST
          - key: http.status_code
            value: "200"
          {{- end }}
        logs: []
        attachedevents: []
      {{- end }}
  {{- end }}
```

## MQE Metrics (No Operation)

**GraphQL schema:** `metrics-v3.graphqls` — `execExpression(expression, entity, duration, debug, dumpDBRsp): ExpressionResult!`

**ExpressionResult type:** `type`(`ExpressionResultType`), `results`(`[MQEValues]`), `error`, `debuggingTrace`
- **MQEValues:** `metric`(`Metadata` with `labels: [KeyValue]`) + `values`(`[MQEValue]`)
- **MQEValue fields:** `id`, `owner`(`Owner`), `value`(String, may be null), `traceID`
- **ExpressionResultType enum:** `UNKNOWN`, `SINGLE_VALUE`, `TIME_SERIES_VALUES`, `SORTED_LIST`, `RECORD_LIST`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql metrics exec \
  --expression=service_sla --service-name=e2e-service-provider
```

**Expected:**
```yaml
debuggingtrace: null
type: TIME_SERIES_VALUES
results:
  {{- contains .results }}
  - metric:
      labels: []
    values:
      {{- contains .values }}
      - id: {{ notEmpty .id }}
        value: {{ notEmpty .value }}
        owner: null
        traceid: null
      - id: {{ notEmpty .id }}
        value: null
        owner: null
        traceid: null
      {{- end }}
  {{- end }}
error: null
```

**Notes:**
- `value: null` entries represent time slots with no data (gaps)
- `id` is typically a timestamp-based identifier
- Including both `notEmpty` and `null` value entries ensures the time series has at least some data

## MQE Metrics with Labels (Percentile)

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql metrics exec \
  --expression="service_percentile{p='50,75,90,95,99'}" --service-name=e2e-service-provider
```

**Expected:**
```yaml
debuggingtrace: null
type: TIME_SERIES_VALUES
results:
  {{- contains .results }}
  - metric:
      labels:
        {{- contains .metric.labels }}
        - key: p
          value: "50"
        {{- end }}
    values:
      {{- contains .values }}
      - id: {{ notEmpty .id }}
        value: {{ notEmpty .value }}
        owner: null
        traceid: null
      {{- end }}
  {{- end }}
error: null
```

## MQE Binary Operations

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql metrics exec \
  --expression="service_sla * 2 / 100 + 500 - 50" --service-name=e2e-service-provider
```

Expression supports: `+`, `-`, `*`, `/`, `>`, `<`, `>=`, `<=`, `==`, `!=`, `&&`, `||`, and functions like `avg()`, `abs()`, `top_n()`, `relabels()`, `aggregate_labels()`.

## Alarm Queries

**GraphQL schema:** `alarm.graphqls` — `getAlarm(duration, scope, keyword, paging, tags): Alarms`

**Alarms type:** `msgs: [AlarmMessage!]!`
- **AlarmMessage fields:** `startTime`(Long), `recoveryTime`(Long), `scope`(Scope), `id`, `name`, `message`, `events`(`[Event]`), `tags`(`[KeyValue]`), `snapshot`(`AlarmSnapshot`)
- **AlarmSnapshot:** `expression`(String) + `metrics`(`[MQEMetric]` with `name` + `results: [MQEValues]`)
- **Event fields** (from `event.graphqls`): `uuid`, `source`(`service`,`serviceInstance`,`endpoint`), `name`, `type`(Normal/Error), `message`, `parameters`(`[KeyValue]`), `startTime`(ms), `endTime`(ms), `layer`

**Query:**
```bash
swctl --display yaml --base-url=http://${oap_host}:${oap_12800}/graphql alarm ls \
  --tags=level=WARNING
```

**Expected:**
```yaml
msgs:
  {{- contains .msgs }}
  - starttime: {{ gt .starttime 0 }}
    scope: Service
    id: {{ b64enc "e2e-service-provider" }}.1
    name: e2e-service-provider
    message: {{ notEmpty .message }}
    tags:
      - key: level
        value: WARNING
    events:
      {{- contains .events }}
      - uuid: {{ notEmpty .uuid }}
        source:
          service: e2e-service-provider
          serviceinstance: ""
          endpoint: ""
        name: Alarm
        type: ""
        message: {{ notEmpty .message }}
        parameters: []
        starttime: {{ gt .starttime 0 }}
        endtime: {{ gt .endtime 0 }}
        layer: GENERAL
      {{- end }}
    snapshot:
      expression: {{ notEmpty .snapshot.expression }}
      metrics:
      {{- contains .snapshot.metrics }}
      - name: {{ notEmpty .name }}
        results:
          {{- contains .results }}
          - metric:
              labels: []
            values:
            {{- contains .values }}
            - id: {{ notEmpty .id }}
              owner: null
              value: {{ .value }}
              traceid: null
            {{- end }}
          {{- end }}
      {{- end }}
  {{- end }}
```

## Log Queries

**GraphQL schema:** `log.graphqls` — `queryLogs(condition, debug): Logs`

**Logs type:** `errorReason`(String), `logs`(`[Log]`), `debuggingTrace`
- **Log fields:** `serviceName`, `serviceId`, `serviceInstanceName`, `serviceInstanceId`, `endpointName`, `endpointId`, `traceId`, `timestamp`(Long), `contentType`(TEXT/JSON/YAML), `content`, `tags`(`[KeyValue]`)

## Event Queries

**GraphQL schema:** `event.graphqls` — `queryEvents(condition): Events`

**Events type:** `events: [Event!]!`
- **Event fields:** `uuid`, `source`(`service`,`serviceInstance`,`endpoint`), `name`, `type`(Normal/Error), `message`, `parameters`(`[KeyValue]`), `startTime`(ms), `endTime`(ms), `layer`

## Hierarchy Queries

**GraphQL schema:** `hierarchy.graphqls` — `getServiceHierarchy(serviceId, layer): ServiceHierarchy!`

**ServiceHierarchy type:** `relations: [HierarchyServiceRelation!]!`
- **HierarchyServiceRelation:** `upperService` + `lowerService` (each: `id`, `name`, `layer`, `normal`)

## swctl Query Types Reference

| Command | Description | GraphQL Query |
|---------|-------------|---------------|
| `service ls` | List all services | `listServices` |
| `layer ls` | List all layers | `listLayers` |
| `instance list --service-name=X` | List instances of a service | `listInstances` |
| `endpoint list --service-name=X` | List endpoints of a service | `findEndpoint` |
| `dependency global` | Global service dependency topology | `getGlobalTopology` |
| `dependency service --service-name=X` | Service-level dependency | `getServiceTopology` |
| `dependency instance --service-name=X --dest-service-name=Y` | Instance-level dependency | `getServiceInstanceTopology` |
| `dependency endpoint --service-name=X --endpoint-name=Y` | Endpoint-level dependency | `getEndpointDependencies` |
| `tv2 ls` | List traces (v2) | `queryTraces` |
| `metrics exec --expression=EXPR --service-name=X` | Execute MQE expression | `execExpression` |
| `alarm ls` | List alarms | `getAlarm` |
| `alarm ls --tags=key=value` | List alarms filtered by tags | `getAlarm` with tags |
