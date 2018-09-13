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

The sample settings of these receivers should be already in default `application.yml`, and also list here
```yaml
receiver-trace:
  default:
    bufferPath: ../buffer/  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: 100 # Unit is MB
    bufferDataMaxFileSize: 500 # Unit is MB
    bufferFileCleanWhenRestart: false # Clean buffer file when backend restart.
receiver-jvm:
  default:
service-mesh:
  default:
istio-telemetry:
  default:
```