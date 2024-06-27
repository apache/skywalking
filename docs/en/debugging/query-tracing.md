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

| Field        | Description                                                                                                      |
|--------------|------------------------------------------------------------------------------------------------------------------|
| spanId       | The unique ID of the span                                                                                        |
| parentSpanId | The parent span ID of the span                                                                                   |
| operation    | The operation name of the span                                                                                   |
| startTime    | The start time of the span. In nanoseconds                                                                       |
| endTime      | The end time of the span. In nanoseconds                                                                         |
| duration     | The duration of the span. In nanoseconds                                                                         |
| msg          | The message of the span, could include additional info such as request condition and response from the Database  |
| error        | The error message of the span, if the span has an error.                                                         |


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

Tracing an avg query with the MQE query expression `avg(service_sla)` from 2024-06-18 11 to 2024-06-18 12 with the step HOUR for the service `mock_a_service` and the service layer `GENERAL`.

```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/mqe?dumpDBRsp=true&expression=avg(service_sla)&startTime=2024-06-18%2011&endTime=2024-06-18%2012&step=HOUR&service=mock_a_service&serviceLayer=GENERAL'
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
  traceId: "a50910fe-c966-4249-87f9-e471e55cf92f"
  condition: "Expression: avg(service_sla), Entity: Entity(scope=null, serviceName=mock_a_service,\
    \ normal=true, serviceInstanceName=null, endpointName=null, processName=null,\
    \ destServiceName=null, destNormal=null, destServiceInstanceName=null, destEndpointName=null,\
    \ destProcessName=null), Duration: Duration(start=2024-06-18 11, end=2024-06-18\
    \ 12, step=HOUR)"
  startTime: 61636369350002
  endTime: 61636374541189
  duration: 5191187
  rootSpan:
    spanId: 0
    parentSpanId: -1
    operation: "MQE query"
    startTime: 61636369352761
    endTime: 61636374539893
    duration: 5187132
    msg: null
    error: null
    childSpans:
      - spanId: 1
        parentSpanId: 0
        operation: "MQE syntax analysis"
        startTime: 61636369357437
        endTime: 61636369443631
        duration: 86194
        msg: null
        error: null
        childSpans: []
      - spanId: 2
        parentSpanId: 0
        operation: "MQE Aggregation OP: avg(service_sla)"
        startTime: 61636369458018
        endTime: 61636374440560
        duration: 4982542
        msg: null
        error: null
        childSpans:
          - spanId: 3
            parentSpanId: 2
            operation: "MQE Metric OP: service_sla"
            startTime: 61636369468839
            endTime: 61636374373771
            duration: 4904932
            msg: null
            error: null
            childSpans:
...
```

### Tracing SkyWalking Trace Query

#### Tracing SkyWalking API queryBasicTraces

- URL: HTTP GET `http://{core restHost}:{core restPort}/debugging/query/queryBasicTraces?{parameters}`.
- Parameters

  | Field             | Description                                                   | Required           |
  |-------------------|---------------------------------------------------------------|--------------------|
  | startTime         | The start time of the query                                   | Yes                |               
  | endTime           | The end time of the query                                     | Yes                |               
  | step              | The query step                                                | Yes                |               
  | service           | The service name                                              | Yes                |               
  | serviceLayer      | The service layer name                                        | Yes                |               
  | serviceInstance   | The service instance name                                     | No                 |               
  | endpoint          | The endpoint name                                             | No                 |               
  | minTraceDuration  | The minimum duration of the trace                             | No                 |               
  | maxTraceDuration  | The maximum duration of the trace                             | No                 |               
  | traceState        | The state of the trace, `ALL`, `SUCCESS`, `ERROR`             | Yes                |               
  | queryOrder        | The order of the query result, `BY_START_TIME`, `BY_DURATION` | Yes                |               
  | tags              | The tags of the trace, `key1=value1,key2=value2`              | No                 |  
  | pageNum           | The page number of the query result                           | Yes                |
  | pageSize          | The page size of the query result                             | Yes                |

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
    execExpression(expression: "avg(service_sla)", entity: {serviceName: "mock_a_service", normal: true}, duration: {start: "2024-06-18 11", end: "2024-06-18 12", step: HOUR}, debug: true, dumpDBRsp: true) {
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
        "traceId": "7caa6529-9a72-4878-ab82-7a9ca032a97b",
        "condition": "Expression: avg(service_sla), Entity: Entity(scope=null, serviceName=mock_a_service, normal=true, serviceInstanceName=null, endpointName=null, processName=null, destServiceName=null, destNormal=null, destServiceInstanceName=null, destEndpointName=null, destProcessName=null), Duration: Duration(start=2024-06-18 11, end=2024-06-18 12, step=HOUR)",
        "startTime": 73040673584523,
        "endTime": 73040678525541,
        "duration": 4941018,
        "spans": [
          {
            "spanId": 0,
            "parentSpanId": -1,
            "operation": "MQE query",
            "startTime": 73040673586770,
            "endTime": 73040678523624,
            "duration": 4936854,
            "msg": null,
            "error": null
          },
          {
            "spanId": 1,
            "parentSpanId": 0,
            "operation": "MQE syntax analysis",
            "startTime": 73040673591566,
            "endTime": 73040673677579,
            "duration": 86013,
            "msg": null,
            "error": null
          },
          {
            "spanId": 2,
            "parentSpanId": 0,
            "operation": "MQE Aggregation OP: avg(service_sla)",
            "startTime": 73040673691700,
            "endTime": 73040678270159,
            "duration": 4578459,
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
### Tracing SkyWalking Trace Query

#### Tracing SkyWalking API queryBasicTraces
- Bundle API: [Trace](../api/query-protocol.md#trace)

```graphql
# Param, if debug is true will enable the query tracing and return DebuggingTrace in the result.
extend type Query {
    # Search segment list with given conditions
    queryBasicTraces(condition: TraceQueryCondition, debug: Boolean): TraceBrief
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
```

- Example

Same as the MQE query tracing, follow the GraphQL protocol and grammar to query the result and get debuggingTrace information, 
just enable the debug parameter to true.

#### Tracing SkyWalking API queryTrace
- Bundle API: [Trace](../api/query-protocol.md#trace)

```graphql  
# Param, if debug is true will enable the query tracing and return DebuggingTrace in the result.
extend type Query {
    # Read the specific trace ID with given trace ID
    queryTrace(traceId: ID!, debug: Boolean): Trace
    ...
}
```

```graphql
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
