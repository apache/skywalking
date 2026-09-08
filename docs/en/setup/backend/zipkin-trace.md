## Zipkin receiver
The Zipkin receiver makes the OAP server work as an alternative Zipkin server implementation for collecting traces. 
It supports Zipkin v1/v2 formats through the HTTP collector and Kafka collector.

**NOTICE, Zipkin trace would not be analyzed like SkyWalking native trace format.**

Use the following config to activate it.
Set `enableHttpCollector` to enable HTTP collector and `enableKafkaCollector` to enable Kafka collector.

```yaml
receiver-zipkin:
  selector: ${SW_RECEIVER_ZIPKIN:default}
  default:
    # Defines a set of span tag keys which are searchable.
    # The max length of key=value should be less than 256 or will be dropped.
    searchableTracesTags: ${SW_ZIPKIN_SEARCHABLE_TAG_KEYS:http.method}
    # The sample rate precision is 1/10000, should be between 0 and 10000
    sampleRate: ${SW_ZIPKIN_SAMPLE_RATE:10000}
    # The maximum spans to be collected per second. 0 means no limit. Spans exceeding this threshold will be dropped.
    maxSpansPerSecond: ${SW_ZIPKIN_MAX_SPANS_PER_SECOND:0}
    ## The below configs are for OAP collect zipkin trace from HTTP
    enableHttpCollector: ${SW_ZIPKIN_HTTP_COLLECTOR_ENABLED:true}
    restHost: ${SW_RECEIVER_ZIPKIN_REST_HOST:0.0.0.0}
    restPort: ${SW_RECEIVER_ZIPKIN_REST_PORT:9411}
    restContextPath: ${SW_RECEIVER_ZIPKIN_REST_CONTEXT_PATH:/}
    restMaxThreads: ${SW_RECEIVER_ZIPKIN_REST_MAX_THREADS:200}
    restIdleTimeOut: ${SW_RECEIVER_ZIPKIN_REST_IDLE_TIMEOUT:30000}
    restAcceptQueueSize: ${SW_RECEIVER_ZIPKIN_REST_QUEUE_SIZE:0}
    ## The below configs are for OAP collect zipkin trace from kafka
    enableKafkaCollector: ${SW_ZIPKIN_KAFKA_COLLECTOR_ENABLED:true}
    kafkaBootstrapServers: ${SW_ZIPKIN_KAFKA_SERVERS:localhost:9092}
    kafkaGroupId: ${SW_ZIPKIN_KAFKA_Group_Id:zipkin}
    kafkaTopic: ${SW_ZIPKIN_KAFKA_TOPIC:zipkin}
    # Kafka consumer config, JSON format as Properties. If it contains the same key with above, would override.
    kafkaConsumerConfig: ${SW_ZIPKIN_KAFKA_CONSUMER_CONFIG:"{\"auto.offset.reset\":\"earliest\",\"enable.auto.commit\":true}"}
    # The Count of the topic consumers
    kafkaConsumers: ${SW_ZIPKIN_KAFKA_CONSUMERS:1}
    kafkaHandlerThreadPoolSize: ${SW_ZIPKIN_KAFKA_HANDLER_THREAD_POOL_SIZE:-1}
    kafkaHandlerThreadPoolQueueSize: ${SW_ZIPKIN_KAFKA_HANDLER_THREAD_POOL_QUEUE_SIZE:-1}

```

## Zipkin query
The Zipkin receiver makes the OAP server work as an alternative Zipkin server implementation for query traces. 
It implemented `ZipkinQueryApiV2` through the HTTP service, supporting Zipkin-lens UI.

Use the following config to activate it.

```yaml
query-zipkin:
  selector: ${SW_QUERY_ZIPKIN:default}
  default:
    # For HTTP server
    restHost: ${SW_QUERY_ZIPKIN_REST_HOST:0.0.0.0}
    restPort: ${SW_QUERY_ZIPKIN_REST_PORT:9412}
    restContextPath: ${SW_QUERY_ZIPKIN_REST_CONTEXT_PATH:/zipkin}
    restMaxThreads: ${SW_QUERY_ZIPKIN_REST_MAX_THREADS:200}
    restIdleTimeOut: ${SW_QUERY_ZIPKIN_REST_IDLE_TIMEOUT:30000}
    restAcceptQueueSize: ${SW_QUERY_ZIPKIN_REST_QUEUE_SIZE:0}
    # Default look back for serviceNames, remoteServiceNames and spanNames, 1 day in millis
    lookback: ${SW_QUERY_ZIPKIN_LOOKBACK:86400000}
    # The Cache-Control max-age (seconds) for serviceNames, remoteServiceNames and spanNames
    namesMaxAge: ${SW_QUERY_ZIPKIN_NAMES_MAX_AGE:300}
    ## The below config are OAP support for zipkin-lens UI
    # Default traces query max size
    uiQueryLimit: ${SW_QUERY_ZIPKIN_UI_QUERY_LIMIT:10}
    # Default look back for search traces, 15 minutes in millis
    uiDefaultLookback: ${SW_QUERY_ZIPKIN_UI_DEFAULT_LOOKBACK:900000}
```

### Query traces from the BanyanDB cold stage
When the BanyanDB `zipkinTrace` group has the cold [lifecycle stage](../../banyandb/stages.md) enabled, the
trace-fetching endpoints accept the following optional query parameters in addition to the standard Zipkin ones.
They are SkyWalking extensions of the Zipkin API: a request without them behaves exactly as before, so existing
Zipkin clients, including the Lens UI, keep working unchanged.

| Endpoint                 | Extra parameters               | Description                                                                                                                                                                                                                        |
|--------------------------|--------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/api/v2/traces`         | `coldStage`                    | `true` queries the cold stage instead of the hot/warm stages, within the standard `endTs`/`lookback` time range. Default `false`.                                                                                                   |
| `/api/v2/trace/{traceId}` | `coldStage`, `endTs`, `lookback` | The standard Zipkin API has no time range for these lookups, so without any of the three parameters BanyanDB searches everything its hot/warm stages retain. Passing any of them bounds the query to `[endTs - lookback, endTs]`, `endTs` defaulting to now and `lookback` to the `lookback` setting above (both in milliseconds), and `coldStage=true` targets the cold stage. |
| `/api/v2/traceMany`      | `coldStage`, `endTs`, `lookback` | Same as `/api/v2/trace/{traceId}`.                                                                                                                                                                                                 |

- `coldStage` is only meaningful for BanyanDB, the other storages ignore it, the same as the `coldStage` flag of
the GraphQL `Duration` input.
- Cold data is older than the hot (and warm) TTL by definition, so always send an `endTs`/`lookback` pair that
covers the time the trace happened. The defaults, now and one day back, normally point at hot data.

For example, fetch a trace that happened 2 to 4 days ago from the cold stage:
```shell
curl "http://127.0.0.1:9412/zipkin/api/v2/trace/fcb10b060c6b2492?coldStage=true&endTs=$(($(date +%s)*1000-172800000))&lookback=172800000"
```

## Lens UI
Lens UI is Zipkin's native browser UI. The OAP distribution does not bundle
it; deploy a standalone Zipkin Lens container against the OAP Zipkin
endpoints if you want a Lens-style trace view. SkyWalking
[Horizon UI](https://github.com/apache/skywalking-horizon-ui) renders its
own trace views over the same OAP trace data.

Zipkin Lens UI source codes could be found [here](https://github.com/openzipkin/zipkin/tree/master/zipkin-lens).
