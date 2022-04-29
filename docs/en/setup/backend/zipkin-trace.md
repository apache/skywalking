## Zipkin receiver
The Zipkin receiver makes the OAP server work as an alternative Zipkin server implementation. It supports Zipkin v1/v2 formats through the HTTP service.
Make sure you use this with `SW_STORAGE=zipkin-elasticsearch` option to activate Zipkin storage implementation.
Once this receiver and storage are activated, SkyWalking's native traces would be ignored, and SkyWalking wouldn't analyze topology, metrics, and endpoint dependency from Zipkin's trace.

Use the following config to activate it.
```yaml
receiver-zipkin:
  selector: ${SW_RECEIVER_ZIPKIN:-}
  default:
    # For HTTP server
    restHost: ${SW_RECEIVER_ZIPKIN_REST_HOST:0.0.0.0}
    restPort: ${SW_RECEIVER_ZIPKIN_REST_PORT:9411}
    restContextPath: ${SW_RECEIVER_ZIPKIN_REST_CONTEXT_PATH:/}
    restMaxThreads: ${SW_RECEIVER_ZIPKIN_REST_MAX_THREADS:200}
    restIdleTimeOut: ${SW_RECEIVER_ZIPKIN_REST_IDLE_TIMEOUT:30000}
    restAcceptQueueSize: ${SW_RECEIVER_ZIPKIN_REST_QUEUE_SIZE:0}
    instanceNameRule: ${SW_RECEIVER_ZIPKIN_INSTANCE_NAME_RULE:[spring.instance_id,node_id]}
    searchableTracesTags: ${SW_ZIPKIN_SEARCHABLE_TAG_KEYS:http.method}
```

NOTE: Zipkin receiver requires `zipkin-elasticsearch` storage implementation to be activated.
Read [this](backend-storage.md#elasticsearch-with-zipkin-trace-extension) doc to learn about Zipkin as a storage option.
