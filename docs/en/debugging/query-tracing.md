# Tracing MQE Execution
SkyWalking OAP provides the metrics query tracing to help users debug and diagnose the query performance for the SkyWalking backend self.

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


## MQE Query Tracing
MQE query tracing used to trace the query performance of the MQE query.

### Debugging through HTTP APIs
The MQE query tracing service is provided within the OAP rest server, 
which could be accessed through HTTP GET `http://{core restHost}:{core restPort}/debugging/query/mqe?{parameters}`.

- Parameters

| Field               | Description                                                                 | Required                   | 
|---------------------|-----------------------------------------------------------------------------|----------------------------|
| dumpDBRsp           | Dump the response from the database, **support Elasticsearch and BanyanDB** | No, default value is false |
| expression          | The MQE query expression                                                    | Yes                        |               
| startTime           | The start time of the query                                                 | Yes                        |               
| endTime             | The end time of the query                                                   | Yes                        |               
| step                | The query step                                                              | Yes                        |               
| service             | The service name                                                            | Yes                        |               
| serviceLayer        | The service layer name                                                      | Yes                        |               
| serviceInstance     | The service instance name                                                   | No                         |               
| endpoint            | The endpoint name                                                           | No                         |               
| process             | The process name                                                            | No                         |               
| destService         | The destination service name                                                | No                         |               
| destServiceInstance | The destination service instance name                                       | No                         |               
| destServiceInstance | The destination service instance name                                       | No                         |               
| destEndpoint        | The destination endpoint name                                               | No                         |               
| destProcess         | The destination process name                                                | No                         |               


The time and step parameters are follow the [Duration](../api/query-protocol.md#duration) format.

- Example

Tracing an avg query with the MQE query expression `avg(service_sla)` from 2024-06-18 11 to 2024-06-18 12 with the step HOUR for the service `mock_a_service` and the service layer `GENERAL`.

```shell
curl -X GET 'http://127.0.0.1:12800/debugging/query/mqe?dumpDBRsp=true&expression=avg(service_sla)&startTime=2024-06-18%2011&endTime=2024-06-18%2012&step=HOUR&service=mock_a_service&serviceLayer=GENERAL'
```

Response will include query result and the execTrace information:

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
execTrace:
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
              - spanId: 4
                parentSpanId: 3
                operation: "Query Service"
                startTime: 61636369481077
                endTime: 61636374187132
                duration: 4706055
                msg: "readMetricsValues, MetricsCondition: MetricsCondition(name=service_sla,\
            \ entity=Entity(scope=null, serviceName=mock_a_service, normal=true, serviceInstanceName=null,\
            \ endpointName=null, processName=null, destServiceName=null, destNormal=null,\
            \ destServiceInstanceName=null, destEndpointName=null, destProcessName=null)),\
            \ Duration: Duration(start=2024-06-18 11, end=2024-06-18 12, step=HOUR)"
                error: null
                childSpans:
                  - spanId: 5
                    parentSpanId: 4
                    operation: "Query Dao: readMetricsValues"
                    startTime: 61636369516511
                    endTime: 61636374186023
                    duration: 4669512
                    msg: "MetricsCondition: MetricsCondition(name=service_sla, entity=Entity(scope=Service,\
              \ serviceName=mock_a_service, normal=true, serviceInstanceName=null,\
              \ endpointName=null, processName=null, destServiceName=null, destNormal=null,\
              \ destServiceInstanceName=null, destEndpointName=null, destProcessName=null)),\
              \ ValueColumnName: percentage, Duration: Duration(start=2024-06-18 11,\
              \ end=2024-06-18 12, step=HOUR)"
                    error: null
                    childSpans:
                      - spanId: 6
                        parentSpanId: 5
                        operation: "Query Elasticsearch"
                        startTime: 61636369738832
                        endTime: 61636374107033
                        duration: 4368201
                        msg: "Condition: indices: [metrics-all-20240618]\n Response: {\"docs\"\
                :[{\"found\":true,\"id\":\"service_sla_2024061811_bW9ja19hX3NlcnZpY2U\\\
                u003d.1\",\"source\":{\"metric_table\":\"service_sla\",\"total\":130,\"\
                percentage\":10000,\"match\":130,\"time_bucket\":2024061811,\"entity_id\"\
                :\"bW9ja19hX3NlcnZpY2U\\u003d.1\"}},{\"found\":false,\"id\":\"service_sla_2024061812_bW9ja19hX3NlcnZpY2U\\\
                u003d.1\"}]}"
                        error: null
                        childSpans: []
```

### Debugging with GraphQL bundled
When querying the metrics though the [Metrics V3 APIs](../api/query-protocol.md#v3-apis),
the MQE query tracing service is also provided within the GraphQL bundled.

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
          {
            "spanId": 3,
            "parentSpanId": 2,
            "operation": "MQE Metric OP: service_sla",
            "startTime": 73040673704725,
            "endTime": 73040678230548,
            "duration": 4525823,
            "msg": null,
            "error": null
          },
          {
            "spanId": 4,
            "parentSpanId": 3,
            "operation": "Query Service",
            "startTime": 73040673717502,
            "endTime": 73040678040891,
            "duration": 4323389,
            "msg": "readMetricsValues, MetricsCondition: MetricsCondition(name=service_sla, entity=Entity(scope=null, serviceName=mock_a_service, normal=true, serviceInstanceName=null, endpointName=null, processName=null, destServiceName=null, destNormal=null, destServiceInstanceName=null, destEndpointName=null, destProcessName=null)), Duration: Duration(start=2024-06-18 11, end=2024-06-18 12, step=HOUR)",
            "error": null
          },
          {
            "spanId": 5,
            "parentSpanId": 4,
            "operation": "Query Dao: readMetricsValues",
            "startTime": 73040673744571,
            "endTime": 73040678039850,
            "duration": 4295279,
            "msg": "MetricsCondition: MetricsCondition(name=service_sla, entity=Entity(scope=Service, serviceName=mock_a_service, normal=true, serviceInstanceName=null, endpointName=null, processName=null, destServiceName=null, destNormal=null, destServiceInstanceName=null, destEndpointName=null, destProcessName=null)), ValueColumnName: percentage, Duration: Duration(start=2024-06-18 11, end=2024-06-18 12, step=HOUR)",
            "error": null
          },
          {
            "spanId": 6,
            "parentSpanId": 5,
            "operation": "Query Elasticsearch",
            "startTime": 73040673916132,
            "endTime": 73040677997181,
            "duration": 4081049,
            "msg": "Condition: indices: [metrics-all-20240618]\n Response: {\"docs\":[{\"found\":true,\"id\":\"service_sla_2024061811_bW9ja19hX3NlcnZpY2U\\u003d.1\",\"source\":{\"metric_table\":\"service_sla\",\"total\":130,\"percentage\":10000,\"match\":130,\"time_bucket\":2024061811,\"entity_id\":\"bW9ja19hX3NlcnZpY2U\\u003d.1\"}},{\"found\":false,\"id\":\"service_sla_2024061812_bW9ja19hX3NlcnZpY2U\\u003d.1\"}]}",
            "error": null
          }
        ]
      }
    }
  }
}
```
