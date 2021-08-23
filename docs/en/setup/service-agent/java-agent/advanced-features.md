# Advanced Features

* Set the settings through system properties for config file override. Read [setting override](Setting-override.md).
* Use gRPC TLS to link backend. See [open TLS](TLS.md)
* Monitor a big cluster by different SkyWalking services. Use [Namespace](Namespace.md) to isolate the context propagation.
* Set client [token](Token-auth.md) if backend open [token authentication](../../backend/backend-token-auth.md).
* Application Toolkit, are a collection of libraries, provided by SkyWalking APM. Using them, you have a bridge between your application and SkyWalking APM agent.
    * If you want your codes to interact with SkyWalking agent, including `getting trace id`, `setting tags`, `propagating custom data` etc.. Try [SkyWalking manual APIs](Application-toolkit-trace.md).
    * If you require customized metrics, try [SkyWalking Meter System Toolkit](Application-toolkit-meter.md).
    * If you want to continue traces across thread manually, use [across thread solution APIs](Application-toolkit-trace-cross-thread.md).
    * If you want to forward MicroMeter/Spring Sleuth metrics to Meter System, use [SkyWalking MicroMeter Register](Application-toolkit-micrometer.md).
    * If you want to use OpenTracing Java APIs, try [SkyWalking OpenTracing compatible tracer](Opentracing.md). More details you could find at http://opentracing.io
    * If you want to tolerate some exceptions, read [tolerate custom exception doc](How-to-tolerate-exceptions.md).
    * If you want to print trace context(e.g. traceId) in your logs, or collect logs, choose the log frameworks, [log4j](Application-toolkit-log4j-1.x.md), [log4j2](Application-toolkit-log4j-2.x.md), [logback](Application-toolkit-logback-1.x.md).
* If you want to specify the path of your agent.config file. Read [set config file through system properties](Specified-agent-config.md)
