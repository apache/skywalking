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

## Lens UI
Lens UI is Zipkin native UI. SkyWalking webapp has bundled it in the binary distribution.
`{webapp IP}:{webapp port}/zipkin` is exposed and accessible for the browser.
Meanwhile, `Iframe` UI component could be used to host Zipkin Lens UI on the SkyWalking booster UI dashboard.(link=/zipkin) 

Zipkin Lens UI source codes could be found [here](https://github.com/openzipkin/zipkin/tree/master/zipkin-lens).
