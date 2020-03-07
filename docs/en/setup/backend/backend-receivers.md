# Choose receiver
Receiver is a concept in SkyWalking backend. All modules, which are responsible for receiving telemetry
or tracing data from other being monitored system, are all being called **Receiver**. Although today, most of 
receivers are using gRPC or HTTPRestful to provide service, actually, whether listening mode or pull mode
could be receiver. Such as a receiver could base on pull data from remote, like Kakfa MQ.

We have following receivers, and `default` implementors are provided in our Apache distribution.
1. **receiver-trace**. gRPC and HTTPRestful services to accept SkyWalking format traces.
1. **receiver-register**. gRPC and HTTPRestful services to provide service, service instance and endpoint register.
1. **service-mesh**. gRPC services accept data from inbound mesh probes.
1. **receiver-jvm**. gRPC services accept JVM metrics data.
1. **istio-telemetry**. Istio telemetry is from Istio official bypass adaptor, this receiver match its gRPC services.
1. **envoy-metric**. Envoy `metrics_service` and `ALS(access log service)` supported by this receiver. OAL script support all GAUGE type metrics. 
1. **receiver_zipkin**. See [details](#zipkin-receiver).
1. **receiver_jaeger**. See [details](#jaeger-receiver).
1. **receiver-profile**. gRPC services accept profile task status and snapshot reporter.

The sample settings of these receivers should be already in default `application.yml`, and also list here
```yaml
receiver-register:
  default:
receiver-trace:
  default:
    bufferPath: ../trace-buffer/  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: 100 # Unit is MB
    bufferDataMaxFileSize: 500 # Unit is MB
    bufferFileCleanWhenRestart: false
    sampleRate: ${SW_TRACE_SAMPLE_RATE:1000} # The sample rate precision is 1/10000. 10000 means 100% sample in default.
receiver-jvm:
  default:
service-mesh:
  default:
    bufferPath: ../mesh-buffer/  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: 100 # Unit is MB
    bufferDataMaxFileSize: 500 # Unit is MB
    bufferFileCleanWhenRestart: false
istio-telemetry:
  default:
envoy-metric:
  default:
receiver_zipkin:
  default:
    host: 0.0.0.0
    port: 9411
    contextPath: /
receiver-profile:
  default:
```

## gRPC/HTTP server for receiver
In default, all gRPC/HTTP services should be served at `core/gRPC` and `core/rest`.
But the `receiver-sharing-server` module provide a way to make all receivers serving at
different ip:port, if you set them explicitly. 
```yaml
receiver-sharing-server:
  default:
    restHost: ${SW_SHARING_SERVER_REST_HOST:0.0.0.0}
    restPort: ${SW_SHARING_SERVER_REST_PORT:12800}
    restContextPath: ${SW_SHARING_SERVER_REST_CONTEXT_PATH:/}
    gRPCHost: ${SW_SHARING_SERVER_GRPC_HOST:0.0.0.0}
    gRPCPort: ${SW_SHARING_SERVER_GRPC_PORT:11800}
```

Notice, if you add these settings, make sure they are not as same as core module,
because gRPC/HTTP servers of core are still used for UI and OAP internal communications.

## Zipkin receiver
Zipkin receiver could work in two different mode.
1. Tracing mode(default). Tracing mode is that, skywalking OAP acts like zipkin collector,
fully supports Zipkin v1/v2 formats through HTTP service,
also provide persistence and query in skywalking UI.
But it wouldn't analysis metrics from them. In most case, I suggest you could use this feature, when metrics come from service mesh.
Notice, in this mode, Zipkin receiver requires `zipkin-elasticsearch` storage implementation active. 
Read [this](backend-storage.md#elasticsearch-6-with-zipkin-trace-extension) to know 
how to active.

Use following config to active.
```yaml
receiver_zipkin:
  default:
    host: ${SW_RECEIVER_ZIPKIN_HOST:0.0.0.0}
    port: ${SW_RECEIVER_ZIPKIN_PORT:9411}
    contextPath: ${SW_RECEIVER_ZIPKIN_CONTEXT_PATH:/}
```

2. Analysis mode(Not production ready), receive Zipkin v1/v2 formats through HTTP service. Transform the trace to skywalking
native format, and analysis like skywalking trace. This feature can't work in production env right now,
because of Zipkin tag/endpoint value unpredictable, we can't make sure it fits production env requirements.

Active `analysis mode`, you should set `needAnalysis` config.
```yaml
receiver_zipkin:
  default:
    host: ${SW_RECEIVER_ZIPKIN_HOST:0.0.0.0}
    port: ${SW_RECEIVER_ZIPKIN_PORT:9411}
    contextPath: ${SW_RECEIVER_ZIPKIN_CONTEXT_PATH:/}
    needAnalysis: true
```

## Jaeger receiver
Jaeger receiver right now only works in `Tracing Mode`, and no analysis.
Jaeger receiver provides extra gRPC host/port, if absent, sharing-server host/port will be used, then core gRPC host/port.
Receiver requires `jaeger-elasticsearch` storage implementation active. 
Read [this](backend-storage.md#elasticsearch-6-with-jaeger-trace-extension) to know how to active.

Right now, you need [jaeger agent](https://www.jaegertracing.io/docs/1.11/architecture/#agent) to batch
send spans to SkyWalking oap server. Read [Jaeger Architecture](https://www.jaegertracing.io/docs/1.11/architecture/)
to get more details.

Active the receiver.
```yaml
receiver_jaeger:
  default:
    gRPCHost: ${SW_RECEIVER_JAEGER_HOST:0.0.0.0}
    gRPCPort: ${SW_RECEIVER_JAEGER_PORT:14250}
``` 