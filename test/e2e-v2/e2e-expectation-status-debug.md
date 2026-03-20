# Status & Debugging Query Expected File Patterns

SkyWalking provides internal status and debugging HTTP endpoints on the core OAP REST port (shared with GraphQL).

**Port:** `${oap_12800}` — Base URL: `http://${oap_host}:${oap_12800}/`
**Module:** `status-query-plugin` (`status-query`)
**Handlers:** `DebuggingHTTPHandler`, `TTLConfigQueryHandler`, `ClusterStatusQueryHandler`, `AlarmStatusQueryHandler`

All debugging endpoints return **YAML-formatted** responses with embedded execution traces.

## Debugging Endpoints

### MQE Debug

**Query:**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/mqe?expression=service_sla&service=e2e-service-provider&serviceLayer=GENERAL&startTime=...&endTime=...&step=MINUTE"
```

**Parameters:** `dumpDBRsp`(bool), `expression`, `startTime`, `endTime`, `step`(DAY/HOUR/MINUTE/SECOND), `coldStage`(bool), `service`, `serviceLayer`, `serviceInstance`, `endpoint`, `process`, `destService`, `destServiceLayer`, `destServiceInstance`, `destEndpoint`, `destProcess`

**Expected (YAML):**
```yaml
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
      {{- end }}
  {{- end }}
error: null
debuggingtrace:
  traceid: {{ notEmpty .debuggingtrace.traceid }}
  condition: {{ notEmpty .debuggingtrace.condition }}
  starttime: {{ gt .debuggingtrace.starttime 0 }}
  endtime: {{ gt .debuggingtrace.endtime 0 }}
  duration: {{ gt .debuggingtrace.duration 0 }}
  spans:
    {{- contains .debuggingtrace.spans }}
    - spanid: {{ ge .spanid 0 }}
      parentspanid: {{ ge .parentspanid -1 }}
      operation: {{ notEmpty .operation }}
      starttime: {{ gt .starttime 0 }}
      endtime: {{ gt .endtime 0 }}
      duration: {{ ge .duration 0 }}
    {{- end }}
```

### Trace Debug

**Query (basic traces):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/trace/queryBasicTraces?service=e2e-service-provider&serviceLayer=GENERAL&startTime=...&endTime=...&step=MINUTE&pageNum=1&pageSize=10"
```

**Parameters:** `service`, `serviceLayer`, `serviceInstance`, `endpoint`, `traceId`, `startTime`, `endTime`, `step`, `minTraceDuration`, `maxTraceDuration`, `traceState`(ALL/SUCCESS/ERROR), `queryOrder`(BY_START_TIME/BY_DURATION), `tags`, `pageNum`, `pageSize`

**Query (single trace):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/trace/queryTrace?traceId=abc123&startTime=...&endTime=...&step=MINUTE"
```

### Topology Debug

**Query (global):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/topology/getGlobalTopology?startTime=...&endTime=...&step=MINUTE&serviceLayer=GENERAL"
```

**Query (services):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/topology/getServicesTopology?startTime=...&endTime=...&step=MINUTE&services=svc1,svc2"
```

**Query (instance):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/topology/getServiceInstanceTopology?startTime=...&endTime=...&step=MINUTE&clientService=svc1&serverService=svc2&clientServiceLayer=GENERAL&serverServiceLayer=GENERAL"
```

**Query (endpoint):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/topology/getEndpointDependencies?startTime=...&endTime=...&step=MINUTE&service=svc1&serviceLayer=GENERAL&endpoint=POST:/users"
```

**Query (process):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/topology/getProcessTopology?startTime=...&endTime=...&step=MINUTE&service=svc1&serviceLayer=GENERAL&instance=inst1"
```

### Zipkin Trace Debug

**Query (traces):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/zipkin/api/v2/traces?serviceName=frontend&limit=10"
```

**Parameters:** `serviceName`, `remoteServiceName`, `spanName`, `annotationQuery`, `minDuration`, `maxDuration`, `endTs`, `lookback`, `limit`(default 10)

**Query (single trace):**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/zipkin/api/v2/trace?traceId=abc123"
```

### Log Debug

**Query:**
```bash
curl "http://${oap_host}:${oap_12800}/debugging/query/log/queryLogs?service=svc1&serviceLayer=GENERAL&startTime=...&endTime=...&step=MINUTE&pageNum=1&pageSize=10"
```

**Parameters:** `service`, `serviceLayer`, `serviceInstance`, `endpoint`, `startTime`, `endTime`, `step`, `coldStage`, `traceId`, `segmentId`, `spanId`, `tags`, `pageNum`, `pageSize`, `keywordsOfContent`, `excludingKeywordsOfContent`, `queryOrder`

### Config Dump

**Query:**
```bash
curl http://${oap_host}:${oap_12800}/debugging/config/dump
```

Returns all booting configurations with secret values masked.

## Status Endpoints

### TTL Config

**Query:**
```bash
curl http://${oap_host}:${oap_12800}/status/config/ttl
```

**Expected:**
```yaml
metricsttl:
  minute: {{ ge .metricsttl.minute 0 }}
  hour: {{ ge .metricsttl.hour 0 }}
  day: {{ ge .metricsttl.day 0 }}
recordsttl:
  normal: {{ ge .recordsttl.normal 0 }}
  trace: {{ ge .recordsttl.trace 0 }}
  log: {{ ge .recordsttl.log 0 }}
```

### Cluster Node List

**Query:**
```bash
curl http://${oap_host}:${oap_12800}/status/cluster/nodes
```

**Expected:**
```yaml
nodes:
  {{- contains .nodes }}
  - {{ notEmpty . }}
  {{- end }}
```

### Alarm Rules

**Query (all rules):**
```bash
curl http://${oap_host}:${oap_12800}/status/alarm/rules
```

**Query (specific rule):**
```bash
curl http://${oap_host}:${oap_12800}/status/alarm/{ruleId}
```

**Query (rule context for entity):**
```bash
curl http://${oap_host}:${oap_12800}/status/alarm/{ruleId}/{entityName}
```

**Expected (cluster response wrapper):**
```yaml
oapinstances:
  {{- contains .oapinstances }}
  - address: {{ notEmpty .address }}
    status: {{ notEmpty .status }}
    errormsg: null
  {{- end }}
```

## Debugging Trace Structure

All `/debugging/` endpoints include a `debuggingtrace` field in the response. This is the OAP internal execution trace (same as GraphQL `DebuggingTrace` type from `common.graphqls`):

```yaml
debuggingtrace:
  traceid: "unique-trace-id"
  condition: "query condition string"
  starttime: 1234567890000000    # nanoseconds
  endtime: 1234567891000000
  duration: 1000000
  spans:
    - spanid: 0
      parentspanid: -1
      operation: "TopologyQueryService.getGlobalTopology"
      starttime: 1234567890000000
      endtime: 1234567891000000
      duration: 1000000
      msg: "optional message"
      error: null
```

The span hierarchy mirrors the internal call chain, useful for diagnosing slow queries or storage issues.
