# Tracing OAP query
SkyWalking OAP provides the metrics/trace/log/topology query tracing to help users debug and diagnose the query performance for the SkyWalking backend self.

## Tracing Structure
- Trace

| Field     | Description                                     |
|-----------|-------------------------------------------------|
| traceId   | The unique ID of the trace                      |
| condition | The query conditions                            |
| startTime | The start time of the trace. In nanoseconds     |
| endTime   | The end time of the trace. In nanoseconds       |
| duration  | The operation name of the trace. in nanoseconds |
| spans     | spans in this trace                             |

- Span

| Field        | Description                                                                                                                                        |
|--------------|----------------------------------------------------------------------------------------------------------------------------------------------------|
| spanId       | The unique ID of the span                                                                                                                          |
| parentSpanId | The parent span ID of the span                                                                                                                     |
| operation    | The operation name of the span                                                                                                                     |
| startTime    | The start time of the span. In nanoseconds, this is a relative time based on different env and implementation                                      |
| endTime      | The end time of the span. In nanoseconds, this is a relative time based on different env and implementation                                                                                                         |
| duration     | The duration of the span. In nanoseconds                                                                                                           |
| msg          | The message of the span, could include additional info such as request condition and response from the Database or Tags from BanyanDB internal trace |
| error        | The error message of the span, if the span has an error.                                                                                           |


## Debugging through HTTP APIs
The query tracing service is provided within the OAP rest server, 
which could be accessed through HTTP GET `http://{core restHost}:{core restPort}/debugging/query/...`.

### Tracing MQE Execution
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/mqe?{parameters}`.
- Parameters

| Field               | Description                                                                 | Required           | 
|---------------------|-----------------------------------------------------------------------------|--------------------|
| dumpDBRsp           | Dump the response from the database, **support Elasticsearch and BanyanDB** | No, default: false |
| expression          | The MQE query expression                                                    | Yes                |               
| startTime           | The start time of the query                                                 | Yes                |               
| endTime             | The end time of the query                                                   | Yes                |               
| step                | The query step                                                              | Yes                |  
| coldStage           | Only for BanyanDB, the flag to query from cold stage, default is false.     | No                 |  
| service             | The service name                                                            | Yes                |               
| serviceLayer        | The service layer name                                                      | Yes                |               
| serviceInstance     | The service instance name                                                   | No                 |               
| endpoint            | The endpoint name                                                           | No                 |               
| process             | The process name                                                            | No                 |               
| destService         | The destination service name                                                | No                 |               
| destServiceInstance | The destination service instance name                                       | No                 |               
| destServiceInstance | The destination service instance name                                       | No                 |               
| destEndpoint        | The destination endpoint name                                               | No                 |               
| destProcess         | The destination process name                                                | No                 |               

The time and step parameters are follow the [Duration](../api/query-protocol.md#duration) format.

- Example

Tracing an avg query with the MQE query expression `avg(service_sla)` from 2024-07-03 to 2024-07-03 with the step DAY for the service `mock_a_service` and the service layer `GENERAL`.

```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/mqe?dumpDBRsp=true&expression=avg(service_sla)&startTime=2024-07-03&endTime=2024-07-03&step=DAY&service=mock_a_service&serviceLayer=GENERAL'
```

Response will include query result and the debuggingTrace information:

```yaml
type: "SINGLE_VALUE"
results:
  - metric:
      labels: []
    values:
      - id: null
        value: "10000"
        traceID: null
        doubleValue: 10000.0
        emptyValue: false
error: null
debuggingTrace:
  traceId: "4f972417-c543-4f7d-a3f1-f5e694cfeb2b"
  condition: "Expression: avg(service_sla), Entity: Entity(scope=null, serviceName=mock_a_service,\
    \ normal=true, serviceInstanceName=null, endpointName=null, processName=null,\
    \ destServiceName=null, destNormal=null, destServiceInstanceName=null, destEndpointName=null,\
    \ destProcessName=null), Duration: Duration(start=2024-07-03, end=2024-07-03,\
    \ step=DAY)"
  startTime: 115828803080350
  endTime: 115828877400237
  duration: 74319887
  rootSpan:
    spanId: 0
    parentSpanId: -1
    operation: "MQE query"
    startTime: 115828803110686
    endTime: 115828877396756
    duration: 74286070
    msg: null
    error: null
    childSpans:
      - spanId: 1
        parentSpanId: 0
        operation: "MQE syntax analysis"
        startTime: 115828803699331
        endTime: 115828805015745
        duration: 1316414
        msg: null
        error: null
        childSpans: []
      - spanId: 2
        parentSpanId: 0
        operation: "MQE Aggregation OP: avg(service_sla)"
        startTime: 115828805052267
        endTime: 115828876877134
        duration: 71824867
        msg: null
        error: null
        childSpans:
          - spanId: 3
            parentSpanId: 2
            operation: "MQE Metric OP: service_sla"
            startTime: 115828805209453
            endTime: 115828875634953
            duration: 70425500
            msg: null
            error: null
            childSpans:
...
```

**Note:** if using the SkyWalking native storage [BanyanDB](../setup/backend/storages/banyandb.md), 
the debuggingTrace will include the BanyanDB internal execution trace info, such as:
```yaml
...
childSpans:
  - spanId: 7
    parentSpanId: 6
    operation: "BanyanDB: measure-grpc"
    startTime: 1720059017222584700
    endTime: 1720059017223492400
    duration: 907700
    msg: "[Tag(key=request, value={\"groups\":[\"measure-default\"], \"\
                  name\":\"service_sla_day\", \"timeRange\":{\"begin\":\"2024-07-02T16:00:00Z\"\
                  , \"end\":\"2024-07-03T16:00:00Z\"}, \"criteria\":{\"condition\"\
                  :{\"name\":\"entity_id\", \"op\":\"BINARY_OP_EQ\", \"value\":{\"\
                  str\":{\"value\":\"bW9ja19hX3NlcnZpY2U=.1\"}}}}, \"tagProjection\"\
                  :{\"tagFamilies\":[{\"name\":\"storage-only\", \"tags\":[\"entity_id\"\
                  ]}]}, \"fieldProjection\":{\"names\":[\"percentage\"]}, \"trace\"\
                  :true})]"
    error: null
    childSpans:
      - spanId: 8
        parentSpanId: 7
        operation: "BanyanDB: data-0ebf3a27de83:17912"
        startTime: 1720059017222821600
        endTime: 1720059017223473500
        duration: 651900
        msg: "[Tag(key=plan, value=IndexScan: startTime=1719936000,endTime=1720022400,Metadata{group=measure-default,name=service_sla_day},conditions=%!s(<nil>);\
                    \ projection=#storage-only:entity_id<TAG_TYPE_STRING>; order=OrderBy:\
                    \ [], sort=SORT_UNSPECIFIED; limit=100 Limit: 0, 100)]"
        error: null
        childSpans:
          - spanId: 9
            parentSpanId: 8
            operation: "BanyanDB: indexScan-group:\"measure-default\"  name:\"\
                      service_sla_day\""
            startTime: 1720059017222879200
            endTime: 1720059017223293300
            duration: 414100
            msg: "[Tag(key=orderBy, value=time SORT_UNSPECIFIED), Tag(key=details,\
                      \ value=IndexScan: startTime=1719936000,endTime=1720022400,Metadata{group=measure-default,name=service_sla_day},conditions=%!s(<nil>);\
                      \ projection=#storage-only:entity_id<TAG_TYPE_STRING>; order=OrderBy:\
                      \ [], sort=SORT_UNSPECIFIED; limit=100)]"
            error: null
            childSpans:
...
```

### Tracing SkyWalking Trace Query

#### Tracing SkyWalking API queryBasicTraces

- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/queryBasicTraces?{parameters}`.
- Parameters

  | Field              | Description                                                               | Required |
  |--------------------|---------------------------------------------------------------------------|----------|
  | startTime          | The start time of the query                                               | Yes      |               
  | endTime            | The end time of the query                                                 | Yes      |               
  | step               | The query step                                                            | Yes      |  
  | coldStage          | Only for BanyanDB, the flag to query from cold stage, default is false.   | No       | 
  | service            | The service name                                                          | Yes      |               
  | serviceLayer       | The service layer name                                                    | Yes      |               
  | serviceInstance    | The service instance name                                                 | No       |               
  | endpoint           | The endpoint name                                                         | No       |               
  | minTraceDuration   | The minimum duration of the trace                                         | No       |               
  | maxTraceDuration   | The maximum duration of the trace                                         | No       |               
  | traceState         | The state of the trace, `ALL`, `SUCCESS`, `ERROR`                         | Yes      |               
  | queryOrder         | The order of the query result, `BY_START_TIME`, `BY_DURATION`             | Yes      |               
  | tags               | The tags of the trace, `key1=value1,key2=value2`                          | No       |  
  | pageNum            | The page number of the query result                                       | Yes      |
  | pageSize           | The page size of the query result                                         | Yes      |

The time and step parameters are follow the [Duration](../api/query-protocol.md#duration) format.

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/trace/queryBasicTraces?startTime=2024-06-26%200900&endTime=2024-06-26%200915&step=MINUTE&service=mock_a_service&serviceLayer=GENERAL&serviceInstance=mock_a_service_instance&traceState=ALL&queryOrder=BY_DURATION&pageNum=1&pageSize=15&tags=http.status_code%3D404%2Chttp.method%3Dget' 
```
Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
traces:
...
debuggingTrace:
...
```

#### Tracing SkyWalking API queryTrace
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/trace/queryTrace?{parameters}`.
- Parameters

  | Field             | Description         | Required           |
  |-------------------|---------------------|--------------------|
  | traceId           | The ID of the trace | Yes                |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/trace/queryTrace?traceId=8211a1d1-de0f-4485-8766-c88866a8f034'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
spans:
...
debuggingTrace:
...
```

#### Tracing SkyWalking API queryTraceFromColdStage
Only for BanyanDB, can be used to query the trace in the cold stage.

- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/trace/queryTraceFromColdStage?{parameters}`.
- Parameters

  | Field           | Description                      | Required        |
  |-----------------|----------------------------------|-----------------|
  | traceId         | The ID of the trace              | Yes             |
  | startTime       | The start time of the query      | Yes             |               
  | endTime         | The end time of the query        | Yes             |               
  | step            | The query step                   | Yes             |

### Tracing Zipkin Trace Query

#### Tracing Zipkin API /api/v2/traces
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/zipkin/api/v2/traces?{parameters}`.
- Parameters
  
  | Field             | Description                       | Required                       |
  |-------------------|-----------------------------------|--------------------------------|
  | serviceName       | The service name                  | No                             |
  | remoteServiceName | The remote service name           | No                             |
  | spanName          | The span name                     | No                             |
  | annotationQuery   | The annotation query              | No                             |
  | minDuration       | The minimum duration of the trace | No                             |
  | maxDuration       | The maximum duration of the trace | No                             |
  | endTs             | The end timestamp of the trace    | No, default, current timestamp |
  | lookback          | The lookback of the trace query   | No, default: 86400000          |
  | limit             | The limit of the trace query      | No, default: 10                |

  All parameters are the same as the Zipkin API `/api/v2/traces`.

- Example

```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/zipkin/api/v2/traces?serviceName=frontend'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
traces:
...
debuggingTrace:
...
```

#### Tracing /api/v2/trace/{traceId}
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/zipkin/api/v2/trace?{parameters}`
- Parameters

  | Field             | Description         | Required           |
  |-------------------|---------------------|--------------------|
  | traceId           | The ID of the trace | Yes                |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/zipkin/api/v2/trace?traceId=fcb10b060c6b2492`
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
spans:
... 
debuggingTrace:
...
```

### Tracing Topology Query

#### Tracing SkyWalking API getGlobalTopology
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/topology/getGlobalTopology?{parameters}`.
- Parameters

  | Field         | Description                                                             | Required |
  |---------------|-------------------------------------------------------------------------|----------|
  | startTime     | The start time of the query                                             | Yes      |               
  | endTime       | The end time of the query                                               | Yes      |               
  | step          | The query step                                                          | Yes      | 
  | coldStage     | Only for BanyanDB, the flag to query from cold stage, default is false. | No       | 
  | serviceLayer  | The service layer name                                                  | No       |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/topology/getGlobalTopology?startTime=2024-07-03&endTime=2024-07-03&step=DAY&serviceLayer=GENERAL'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
nodes:
...
calls:
...
debuggingTrace:
...
```

#### Tracing SkyWalking API getServicesTopology
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/topology/getServicesTopology?{parameters}`.
- Parameters

  | Field         | Description                                                                 | Required |
  |---------------|-----------------------------------------------------------------------------|----------|
  | startTime     | The start time of the query                                                 | Yes      |               
  | endTime       | The end time of the query                                                   | Yes      |               
  | step          | The query step                                                              | Yes      |
  | coldStage     | Only for BanyanDB, the flag to query from cold stage, default is false.     | No       | 
  | serviceLayer  | The service layer name                                                      | Yes      |
  | services      | The services names list, separate by comma `mock_a_service, mock_b_service` | Yes      |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/topology/getServicesTopology?startTime=2024-07-03&endTime=2024-07-03&step=DAY&serviceLayer=GENERAL&services=mock_a_service%2Cmock_b_service'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
nodes:
...
calls:
...
debuggingTrace:
...
```

#### Tracing SkyWalking API getServiceInstanceTopology
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/topology/getServiceInstanceTopology?{parameters}`.
- Parameters

  | Field              | Description                                                             | Required |
  |--------------------|-------------------------------------------------------------------------|----------|
  | startTime          | The start time of the query                                             | Yes      |               
  | endTime            | The end time of the query                                               | Yes      |               
  | step               | The query step                                                          | Yes      |  
  | coldStage          | Only for BanyanDB, the flag to query from cold stage, default is false. | No       | 
  | clientService      | The client side service name                                            | Yes      |
  | serverService      | The server side service name                                            | Yes      |
  | clientServiceLayer | The client side service layer name                                      | Yes      |
  | serverServiceLayer | The server side service layer name                                      | Yes      |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/topology/getServiceInstanceTopology?startTime=2024-07-03&endTime=2024-07-03&step=DAY&clientService=mock_a_service&serverService=mock_b_service&clientServiceLayer=GENERAL&serverServiceLayer=GENERAL'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
nodes:
...
calls:
...
debuggingTrace:
...
```

#### Tracing SkyWalking API getEndpointDependencies
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/topology/getEndpointDependencies?{parameters}`.
- Parameters

  | Field          | Description                                                             | Required |
  |----------------|-------------------------------------------------------------------------|----------|
  | startTime      | The start time of the query                                             | Yes      |               
  | endTime        | The end time of the query                                               | Yes      |               
  | step           | The query step                                                          | Yes      |  
  | coldStage      | Only for BanyanDB, the flag to query from cold stage, default is false. | No       | 
  | service        | The service name                                                        | Yes      |
  | serviceLayer   | The service layer name                                                  | Yes      |
  | endpoint       | The endpoint name                                                       | Yes      |

- Example
- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/topology/getEndpointDependencies?startTime=2024-07-03&endTime=2024-07-03&step=DAY&service=mock_a_service&serviceLayer=GENERAL&endpoint=%2Fdubbox-case%2Fcase%2Fdubbox-rest%2F404-test'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
nodes:
...
calls:
...
debuggingTrace:
...
```

#### Tracing SkyWalking API getProcessTopology
- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/topology/getProcessTopology?{parameters}`.
- Parameters

  | Field        | Description                                                             | Required |
  |--------------|-------------------------------------------------------------------------|----------|
  | startTime    | The start time of the query                                             | Yes      |               
  | endTime      | The end time of the query                                               | Yes      |               
  | step         | The query step                                                          | Yes      | 
  | coldStage    | Only for BanyanDB, the flag to query from cold stage, default is false. | No       | 
  | service      | The service name                                                        | Yes      |
  | serviceLayer | The service layer name                                                  | Yes      |
  | instance     | The instance name                                                       | Yes      |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/topology/getProcessTopology?startTime=2024-07-03&endTime=2024-07-03&step=DAY&service=mock_a_service&serviceLayer=GENERAL&instance=mock_a_service_instance'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
nodes:
...
calls:
...
debuggingTrace:
...
```

### Tracing Log Query

#### Tracing SkyWalking API queryLogs
 URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/log/queryLogs?{parameters}`.
 Parameters

  | Field                      | Description                                                             | Required                      |
  |----------------------------|-------------------------------------------------------------------------|-------------------------------|
  | startTime                  | The start time of the query                                             | Yes, unless traceId not empty |               
  | endTime                    | The end time of the query                                               | Yes, unless traceId not empty |               
  | step                       | The query step                                                          | Yes, unless traceId not empty | 
  | coldStage                  | Only for BanyanDB, the flag to query from cold stage, default is false. | No                            | 
  | service                    | The service name                                                        | No, require serviceLayer      |               
  | serviceLayer               | The service layer name                                                  | No                            |  
  | serviceInstance            | The service instance name                                               | No, require service           |               
  | endpoint                   | The endpoint name                                                       | No, require service           |               
  | traceId                    | The trace ID                                                            | No                            |               
  | segmentId                  | The segment ID                                                          | No, require traceId           |
  | spanId                     | The span ID                                                             | No, require traceId           | 
  | queryOrder                 | The order of the query result, `ASC`, `DES`                             | No, default `DES`             |               
  | tags                       | The tags of the trace, `key1=value1,key2=value2`                        | No                            |  
  | pageNum                    | The page number of the query result                                     | Yes                           |
  | pageSize                   | The page size of the query result                                       | Yes                           |
  | keywordsOfContent          | The keywords of the log content, `keyword1,keyword2`                    | No                            |
  | excludingKeywordsOfContent | The excluding keywords of the log content, `keyword1,keyword2`          | No                            |

- Example
```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/log/queryLogs?service=e2e-service-provider&serviceLayer=GENERAL&startTime=2024-07-09&endTime=2024-07-09&step=DAY&pageNum=1&pageSize=15&queryOrder=ASC&tags=level%3DINFO'
```

Response will include query result and the debuggingTrace information, the debuggingTrace information is the same as the MQE query tracing:

```yaml
logs:
...
debuggingTrace:
...
```

## Debugging with GraphQL bundled
When querying the metrics though the [GraphQL APIs](../api/query-protocol.md),
the query tracing service is also provided within the GraphQL bundled.

### Tracing MQE Execution
- Bundle API: [Metrics V3 APIs](../api/query-protocol.md#v3-apis)

```graphql
extend type Query {
    ...
    # Param, if debug is true will enable the query tracing and return DebuggingTrace in the ExpressionResult.
    # Param, if dumpDBRsp is true the database response will dump into the DebuggingTrace span message.
    execExpression(expression: String!, entity: Entity!, duration: Duration!, debug: Boolean, dumpDBRsp: Boolean): ExpressionResult!
}
```

```graphql
type ExpressionResult {
    ...
    debuggingTrace: DebuggingTrace
}
```

- Example

Query the metrics using the above condition through the GraphQL and tracing the query:
```text
http://127.0.0.1:12800/graphql
```

```graphql
{
    execExpression(expression: "avg(service_sla)", entity: {serviceName: "mock_a_service", normal: true}, duration: {start: "2024-07-03", end: "2024-07-03", step: DAY}, debug: true, dumpDBRsp: true) {
        type
        error
        results {
            metric {
                labels {
                    key
                    value
                }
            }
            values {
                id
                value
                traceID
            }
        }
        debuggingTrace {
            traceId
            condition
            startTime
            endTime
            duration
            spans {
                spanId
                parentSpanId
                operation
                startTime
                endTime
                duration
                msg
                error
            }
        }
    }
}


```
Response will include query result and the execTrace information:

```json
{
  "data": {
    "execExpression": {
      "type": "SINGLE_VALUE",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": []
          },
          "values": [
            {
              "id": null,
              "value": "10000",
              "traceID": null
            }
          ]
        }
      ],
      "debuggingTrace": {
        "traceId": "3116ffe3-ee9c-4047-9f22-c135c237aad5",
        "condition": "Expression: avg(service_sla), Entity: Entity(scope=null, serviceName=mock_a_service, normal=true, serviceInstanceName=null, endpointName=null, processName=null, destServiceName=null, destNormal=null, destServiceInstanceName=null, destEndpointName=null, destProcessName=null), Duration: Duration(start=2024-07-03, end=2024-07-03, step=DAY)",
        "startTime": 117259274324665,
        "endTime": 117259279847720,
        "duration": 5523055,
        "spans": [
          {
            "spanId": 0,
            "parentSpanId": -1,
            "operation": "MQE query",
            "startTime": 117259274328719,
            "endTime": 117259279846559,
            "duration": 5517840,
            "msg": null,
            "error": null
          },
          {
            "spanId": 1,
            "parentSpanId": 0,
            "operation": "MQE syntax analysis",
            "startTime": 117259274333084,
            "endTime": 117259274420159,
            "duration": 87075,
            "msg": null,
            "error": null
          },
          {
            "spanId": 2,
            "parentSpanId": 0,
            "operation": "MQE Aggregation OP: avg(service_sla)",
            "startTime": 117259274433533,
            "endTime": 117259279812549,
            "duration": 5379016,
            "msg": null,
            "error": null
          },
...
        ]
      }
    }
  }
}
```
**Note:** if using the SkyWalking native storage [BanyanDB](../setup/backend/storages/banyandb.md),
the debuggingTrace will include the BanyanDB internal execution trace info, such as:
```json
...
{
  "spanId": 7,
  "parentSpanId": 6,
  "operation": "BanyanDB: measure-grpc",
  "startTime": 1720060447687765300,
  "endTime": 1720060447688830200,
  "duration": 1064900,
  "msg": "[Tag(key=request, value={\"groups\":[\"measure-default\"], \"name\":\"service_sla_day\", \"timeRange\":{\"begin\":\"2024-07-02T16:00:00Z\", \"end\":\"2024-07-03T16:00:00Z\"}, \"criteria\":{\"condition\":{\"name\":\"entity_id\", \"op\":\"BINARY_OP_EQ\", \"value\":{\"str\":{\"value\":\"bW9ja19hX3NlcnZpY2U=.1\"}}}}, \"tagProjection\":{\"tagFamilies\":[{\"name\":\"storage-only\", \"tags\":[\"entity_id\"]}]}, \"fieldProjection\":{\"names\":[\"percentage\"]}, \"trace\":true})]",
  "error": null
},
{
"spanId": 8,
"parentSpanId": 7,
"operation": "BanyanDB: data-0ebf3a27de83:17912",
"startTime": 1720060447687956700,
"endTime": 1720060447688810000,
"duration": 853300,
"msg": "[Tag(key=plan, value=IndexScan: startTime=1719936000,endTime=1720022400,Metadata{group=measure-default,name=service_sla_day},conditions=%!s(<nil>); projection=#storage-only:entity_id<TAG_TYPE_STRING>; order=OrderBy: [], sort=SORT_UNSPECIFIED; limit=100 Limit: 0, 100)]",
"error": null
},
{
"spanId": 9,
"parentSpanId": 8,
"operation": "BanyanDB: indexScan-group:\"measure-default\"  name:\"service_sla_day\"",
"startTime": 1720060447687997400,
"endTime": 1720060447688664700,
"duration": 667300,
"msg": "[Tag(key=orderBy, value=time SORT_UNSPECIFIED), Tag(key=details, value=IndexScan: startTime=1719936000,endTime=1720022400,Metadata{group=measure-default,name=service_sla_day},conditions=%!s(<nil>); projection=#storage-only:entity_id<TAG_TYPE_STRING>; order=OrderBy: [], sort=SORT_UNSPECIFIED; limit=100)]",
"error": null
},
{
"spanId": 10,
"parentSpanId": 9,
"operation": "BanyanDB: seriesIndex.Search",
"startTime": 1720060447688023800,
"endTime": 1720060447688101800,
"duration": 78000,
"msg": "[]",
"error": null
},
...
```

### Tracing SkyWalking Trace Query

#### Tracing SkyWalking API queryBasicTraces
- Bundle API: [Trace](../api/query-protocol.md#trace)

```graphql
# Param, if debug is true will enable the query tracing and return DebuggingTrace in the result.
extend type Query {
  # Search segment list with given conditions
  queryBasicTraces(condition: TraceQueryCondition, debug: Boolean): TraceBrief
  # Read the specific trace ID with given trace ID
  queryTrace(traceId: ID!, debug: Boolean): Trace
...
}
```

```graphql
# The list of traces
type TraceBrief {
...
    #For OAP internal query debugging
    debuggingTrace: DebuggingTrace
}

# The trace represents a distributed trace, includes all segments and spans.
type Trace {
...
    #For OAP internal query debugging
    debuggingTrace: DebuggingTrace
}
```

- Example

Same as the MQE query tracing, follow the GraphQL protocol and grammar to query the result and get debuggingTrace information,
just enable the debug parameter to true.

### Tracing Topology Query
- Bundle API: [Topology](../api/query-protocol.md#topology)

```graphql
# Param, if debug is true will enable the query tracing and return DebuggingTrace in the result.
extend type Query {
    # Query the global topology
    # When layer is specified, the topology of this layer would be queried
    getGlobalTopology(duration: Duration!, layer: String, debug: Boolean): Topology
    # Query the topology, based on the given service
    getServiceTopology(serviceId: ID!, duration: Duration!, debug: Boolean): Topology
    # Query the topology, based on the given services.
    # `#getServiceTopology` could be replaced by this.
    getServicesTopology(serviceIds: [ID!]!, duration: Duration!, debug: Boolean): Topology
    # Query the instance topology, based on the given clientServiceId and serverServiceId
    getServiceInstanceTopology(clientServiceId: ID!, serverServiceId: ID!, duration: Duration!, debug: Boolean): ServiceInstanceTopology
...
    # v2 of getEndpointTopology
    getEndpointDependencies(endpointId: ID!, duration: Duration!, debug: Boolean): EndpointTopology
    # Query the topology, based on the given instance
    getProcessTopology(serviceInstanceId: ID!, duration: Duration!, debug: Boolean): ProcessTopology
}
```

```graphql
# The overview topology of the whole application cluster or services,
type Topology {
  nodes: [Node!]!
  calls: [Call!]!
  debuggingTrace: DebuggingTrace
}

# The instance topology based on the given serviceIds
type ServiceInstanceTopology {
  nodes: [ServiceInstanceNode!]!
  calls: [Call!]!
  debuggingTrace: DebuggingTrace
}

# The endpoint topology
type EndpointTopology {
  nodes: [EndpointNode!]!
  calls: [Call!]!
  debuggingTrace: DebuggingTrace
}

# The process topology
type ProcessTopology {
  nodes: [ProcessNode!]!
  calls: [Call!]!
  debuggingTrace: DebuggingTrace
}
```

- Example
Same as the MQE query tracing, follow the GraphQL protocol and grammar to query the result and get debuggingTrace information,
just enable the debug parameter to true.

### Tracing Log Query
- Bundle API: [Log](../api/query-protocol.md#logs)

```graphql
extend type Query {
...
    queryLogs(condition: LogQueryCondition, debug: Boolean): Logs
...
}
```

```graphql
type Logs {
    # When this field is not empty, frontend should display it in UI
    errorReason: String
    logs: [Log!]!
    debuggingTrace: DebuggingTrace
}
```

- Example
Same as the MQE query tracing, follow the GraphQL protocol and grammar to query the result and get debuggingTrace information,
just enable the debug parameter to true.
