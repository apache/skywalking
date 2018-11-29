# Choose receiver
Receiver is a concept in SkyWalking backend. All modules, which are responsible for receiving telemetry
or tracing data from other being monitored system, are all being called **Receiver**. Although today, most of 
receivers are using gRPC or HTTPRestful to provide service, actually, whether listening mode or pull mode
could be receiver. Such as a receiver could base on pull data from remote, like Kakfa MQ.

We have following receivers, and `default` implementors are provided in our Apache distribution.zzz
1. **receiver-trace**. gRPC and HTTPRestful services to accept SkyWalking format traces.
1. **receiver-register**. gRPC and HTTPRestful services to provide service, service instance and endpoint register.
1. **service-mesh**. gRPC services accept data from inbound mesh probes.
1. **istio-telemetry**. Istio telemetry is from Istio official bypass adaptor, this receiver match its gRPC services.
1. **receiver-jvm**. gRPC services accept JVM metric data.
1. **receiver_zipkin**. HTTP service accepts Span in Zipkin v1 and v2 formats. Notice, this receiver only
works as expected in backend single node mode. Cluster mode is not supported. Welcome anyone to improve this.

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
receiver_zipkin:
  default:
    host: 0.0.0.0
    port: 9411
    contextPath: /
```