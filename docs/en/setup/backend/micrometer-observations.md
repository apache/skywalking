# MicroMeter Observations setup

Micrometer Observation is part of the Micrometer project and contains the Observation API.
SkyWalking integrates its MicroMeter 1.10 APIs so that it can send metrics to the Skywalking [Meter System](./../../concepts-and-designs/meter.md).

Follow Java agent [Observations docs](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/application-toolkit-micrometer-1.10/) to set up agent in the Spring first. 

## Set up backend receiver

1. Make sure to enable meter receiver in `application.yml`.
```yaml
receiver-meter:
  selector: ${SW_RECEIVER_METER:default}
  default:
```

2. Configure the meter config file. It already has the [spring sleuth meter config](../../../../oap-server/server-starter/src/main/resources/meter-analyzer-config/spring-sleuth.yaml).
   If you have a customized meter at the agent side, please configure the meter using the steps set out in the [meter document](backend-meter.md#meters-configure).

3. Enable Spring sleuth config in `application.yml`.
```yaml
agent-analyzer:
  selector: ${SW_AGENT_ANALYZER:default}
  default:
    meterAnalyzerActiveFiles: ${SW_METER_ANALYZER_ACTIVE_FILES:spring-sleuth}
```

## Dashboard configuration

SkyWalking provides the Spring Sleuth dashboard by default under the general service instance, which contains the metrics provided by Spring Sleuth by default.
Once you have added customized metrics in the application and configuration the meter config file in the backend. Please following
the [customized dashboard documentation](../../ui/README.md#metrics) to add the metrics in the dashboard.

## Supported meter

Three types of information are supported: Application, System, and JVM.

1. Application: HTTP request count and duration, JDBC max/idle/active connection count, and Tomcat session active/reject count.
1. System: CPU system/process usage, OS system load, and OS process file count.
1. JVM: GC pause count and duration, memory max/used/committed size, thread peak/live/daemon count, and classes loaded/unloaded count.
