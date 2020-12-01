
# Choose receiver
Receiver is a concept in SkyWalking backend. All modules, which are responsible for receiving telemetry
or tracing data from other being monitored system, are all being called **Receiver**. If you are looking for the pull mode,
Take a look at [fetcher document](backend-fetcher.md).

We have following receivers, and `default` implementors are provided in our Apache distribution.
1. **receiver-trace**. gRPC and HTTPRestful services to accept SkyWalking format traces.
1. **receiver-register**. gRPC and HTTPRestful services to provide service, service instance and endpoint register.
1. **service-mesh**. gRPC services accept data from inbound mesh probes.
1. **receiver-jvm**. gRPC services accept JVM metrics data.
1. **envoy-metric**. Envoy `metrics_service` and `ALS(access log service)` supported by this receiver. OAL script support all GAUGE type metrics.
1. **receiver-profile**. gRPC services accept profile task status and snapshot reporter. 
1. **receiver_zipkin**. See [details](#zipkin-receiver).
1. **receiver_jaeger**. See [details](#jaeger-receiver).
1. **receiver-otel**. See [details](#opentelemetry-receiver).
1. **receiver-meter**. See [details](backend-meter.md).
1. **receiver-browser**. gRPC services to accept browser performance data and error log.

The sample settings of these receivers should be already in default `application.yml`, and also list here
```yaml
receiver-register:
  selector: ${SW_RECEIVER_REGISTER:default}
  default:

receiver-trace:
  selector: ${SW_RECEIVER_TRACE:default}
  default:

receiver-jvm:
  selector: ${SW_RECEIVER_JVM:default}
  default:

service-mesh:
  selector: ${SW_SERVICE_MESH:default}
  default:

envoy-metric:
  selector: ${SW_ENVOY_METRIC:default}
  default:
    acceptMetricsService: ${SW_ENVOY_METRIC_SERVICE:true}
    alsHTTPAnalysis: ${SW_ENVOY_METRIC_ALS_HTTP_ANALYSIS:""}

receiver_zipkin:
  selector: ${SW_RECEIVER_ZIPKIN:-}
  default:
    host: ${SW_RECEIVER_ZIPKIN_HOST:0.0.0.0}
    port: ${SW_RECEIVER_ZIPKIN_PORT:9411}
    contextPath: ${SW_RECEIVER_ZIPKIN_CONTEXT_PATH:/}
    jettyMinThreads: ${SW_RECEIVER_ZIPKIN_JETTY_MIN_THREADS:1}
    jettyMaxThreads: ${SW_RECEIVER_ZIPKIN_JETTY_MAX_THREADS:200}
    jettyIdleTimeOut: ${SW_RECEIVER_ZIPKIN_JETTY_IDLE_TIMEOUT:30000}
    jettyAcceptorPriorityDelta: ${SW_RECEIVER_ZIPKIN_JETTY_DELTA:0}
    jettyAcceptQueueSize: ${SW_RECEIVER_ZIPKIN_QUEUE_SIZE:0}

receiver-profile:
  selector: ${SW_RECEIVER_PROFILE:default}
  default:

receiver-browser:
  selector: ${SW_RECEIVER_BROWSER:default}
  default:
    sampleRate: ${SW_RECEIVER_BROWSER_SAMPLE_RATE:10000}
```

## gRPC/HTTP server for receiver
In default, all gRPC/HTTP services should be served at `core/gRPC` and `core/rest`.
But the `receiver-sharing-server` module provide a way to make all receivers serving at
different ip:port, if you set them explicitly. 
```yaml
receiver-sharing-server:
  selector: ${SW_RECEIVER_SHARING_SERVER:default}
  default:
    host: ${SW_RECEIVER_JETTY_HOST:0.0.0.0}
    contextPath: ${SW_RECEIVER_JETTY_CONTEXT_PATH:/}
    authentication: ${SW_AUTHENTICATION:""}
    jettyMinThreads: ${SW_RECEIVER_SHARING_JETTY_MIN_THREADS:1}
    jettyMaxThreads: ${SW_RECEIVER_SHARING_JETTY_MAX_THREADS:200}
    jettyIdleTimeOut: ${SW_RECEIVER_SHARING_JETTY_IDLE_TIMEOUT:30000}
    jettyAcceptorPriorityDelta: ${SW_RECEIVER_SHARING_JETTY_DELTA:0}
    jettyAcceptQueueSize: ${SW_RECEIVER_SHARING_JETTY_QUEUE_SIZE:0}
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
  selector: ${SW_RECEIVER_ZIPKIN:-}
  default:
    host: ${SW_RECEIVER_ZIPKIN_HOST:0.0.0.0}
    port: ${SW_RECEIVER_ZIPKIN_PORT:9411}
    contextPath: ${SW_RECEIVER_ZIPKIN_CONTEXT_PATH:/}
    jettyMinThreads: ${SW_RECEIVER_ZIPKIN_JETTY_MIN_THREADS:1}
    jettyMaxThreads: ${SW_RECEIVER_ZIPKIN_JETTY_MAX_THREADS:200}
    jettyIdleTimeOut: ${SW_RECEIVER_ZIPKIN_JETTY_IDLE_TIMEOUT:30000}
    jettyAcceptorPriorityDelta: ${SW_RECEIVER_ZIPKIN_JETTY_DELTA:0}
    jettyAcceptQueueSize: ${SW_RECEIVER_ZIPKIN_QUEUE_SIZE:0}
```

2. Analysis mode(Not production ready), receive Zipkin v1/v2 formats through HTTP service. Transform the trace to skywalking
native format, and analysis like skywalking trace. This feature can't work in production env right now,
because of Zipkin tag/endpoint value unpredictable, we can't make sure it fits production env requirements.

Active `analysis mode`, you should set `needAnalysis` config.
```yaml
receiver_zipkin:
  selector: ${SW_RECEIVER_ZIPKIN:-}
  default:
    host: ${SW_RECEIVER_ZIPKIN_HOST:0.0.0.0}
    port: ${SW_RECEIVER_ZIPKIN_PORT:9411}
    contextPath: ${SW_RECEIVER_ZIPKIN_CONTEXT_PATH:/}
    jettyMinThreads: ${SW_RECEIVER_ZIPKIN_JETTY_MIN_THREADS:1}
    jettyMaxThreads: ${SW_RECEIVER_ZIPKIN_JETTY_MAX_THREADS:200}
    jettyIdleTimeOut: ${SW_RECEIVER_ZIPKIN_JETTY_IDLE_TIMEOUT:30000}
    jettyAcceptorPriorityDelta: ${SW_RECEIVER_ZIPKIN_JETTY_DELTA:0}
    jettyAcceptQueueSize: ${SW_RECEIVER_ZIPKIN_QUEUE_SIZE:0}
    needAnalysis: true    
```

NOTICE, Zipkin receiver is only provided in `apache-skywalking-apm-x.y.z.tar.gz` tar.

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
  selector: ${SW_RECEIVER_JAEGER:-}
  default:
    gRPCHost: ${SW_RECEIVER_JAEGER_HOST:0.0.0.0}
    gRPCPort: ${SW_RECEIVER_JAEGER_PORT:14250}
``` 

NOTICE, Jaeger receiver is only provided in `apache-skywalking-apm-x.y.z.tar.gz` tar.

## OpenTelemetry receiver

OpenTelemetry receiver supports to ingest agent metrics by meter-system. OAP can load the configuration at bootstrap. 
If the new configuration is not well-formed, OAP fails to start up. The files are located at `$CLASSPATH/otel-<handler>-rules`.
Eg, the `oc` handler loads fules from `$CLASSPATH/otel-oc-rules`, 

Supported handlers:
    * `oc`: [OpenCensus](https://github.com/open-telemetry/opentelemetry-collector/blob/master/exporter/opencensusexporter/README.md) gRPC service handler.

The rule file should be in YAML format, defined by the scheme described in [prometheus-fetcher](./backend-fetcher.md).
Notice, `receiver-otel` only support `group`, `defaultMetricLevel` and `metricsRules` nodes of scheme due to the push mode it opts to.

To active the `oc` handler and `istio` relevant rules:
```yaml
receiver-otel:
  selector: ${SW_OTEL_RECEIVER:default}
  default:
    enabledHandlers: ${SW_OTEL_RECEIVER_ENABLED_HANDLERS:"oc"}
    enabledOcRules: ${SW_OTEL_RECEIVER_ENABLED_OC_RULES:"istio-controlplane"}
```

## Meter receiver

Meter receiver supports accept the metrics into the meter-system. OAP can load the configuration at bootstrap. 

The file is written in YAML format, defined by the scheme described in [backend-meter](./backend-meter.md).

To active the `default` implementation:
```yaml
receiver-meter:
  selector: ${SW_RECEIVER_METER:default}
  default:
```
