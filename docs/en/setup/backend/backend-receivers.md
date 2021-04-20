
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
1. **receiver-otel**. See [details](#opentelemetry-receiver). Receiver for analyzing metrics data from OpenTelemetry
1. **receiver-meter**. See [details](backend-meter.md). Receiver for analyzing metrics in SkyWalking native meter format.
1. **receiver-browser**. gRPC services to accept browser performance data and error log.
1. **receiver-log**. Receiver for native log format. Read [Log Analyzer](log-analyzer.md) for advanced features. 
1. **configuration-discovery**. gRPC services handle configurationDiscovery.
1. **receiver-event**. gRPC services to handle events data.
1. **receiver-zabbix**. See [details](backend-zabbix.md).
1. Experimental receivers. 
    1. **receiver_zipkin**. See [details](#zipkin-receiver).

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

log-analyzer:
   selector: ${SW_LOG_ANALYZER:default}
   default:
      lalFiles: ${SW_LOG_LAL_FILES:default}
      malFiles: ${SW_LOG_MAL_FILES:""}
  
configuration-discovery:
  selector: ${SW_CONFIGURATION_DISCOVERY:default}
  default:

receiver-event:
   selector: ${SW_RECEIVER_EVENT:default}
   default:

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
The receiver adds labels with `key = node_identifier_host_name` and `key = node_identifier_pid` to the collected data samples，
and values from `Node.identifier.host_name` and `Node.identifier.pid` defined in opencensus agent proto,
to be the identification of the metric data.

| Rule Name | Description | Configuration File | Data Source |
|----|----|-----|----|
|istio-controlplane| Metrics of Istio control panel | otel-oc-rules/istio-controlplane.yaml | Istio Control Panel -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|oap| Metrics of SkyWalking OAP server itself | otel-oc-rules/oap.yaml | SkyWalking OAP Server(SelfObservability) -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|vm| Metrics of VMs | otel-oc-rules/vm.yaml | Prometheus node-exporter(VMs) -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|k8s-cluster| Metrics of K8s cluster | otel-oc-rules/k8s-cluster.yaml | K8s kube-state-metrics -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|k8s-node| Metrics of K8s cluster | otel-oc-rules/k8s-node.yaml | cAdvisor & K8s kube-state-metrics -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |
|k8s-service| Metrics of K8s cluster | otel-oc-rules/k8s-service.yaml | cAdvisor & K8s kube-state-metrics -> OpenTelemetry Collector --OC format--> SkyWalking OAP Server |

## Meter receiver

Meter receiver supports accept the metrics into the meter-system. OAP can load the configuration at bootstrap. 

The file is written in YAML format, defined by the scheme described in [backend-meter](./backend-meter.md).

To active the `default` implementation:
```yaml
receiver-meter:
  selector: ${SW_RECEIVER_METER:default}
  default:
```

## Zipkin receiver
Zipkin receiver makes the OAP server as an alternative Zipkin server implementation. It supports Zipkin v1/v2 formats through HTTP service.
Make sure you use this with `SW_STORAGE=zipkin-elasticsearch7` option to activate Zipkin storage implementation.
Once this receiver and storage activated, SkyWalking native traces would be ignored, and SkyWalking wouldn't analysis topology, metrics, endpoint
dependency from Zipkin's trace. 

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

NOTICE, Zipkin receiver is only provided in `apache-skywalking-apm-es7-x.y.z.tar.gz` tar.
And this requires `zipkin-elasticsearch7` storage implementation active.
Read [this](backend-storage.md#elasticsearch-7-with-zipkin-trace-extension) doc to know Zipkin as storage option.
