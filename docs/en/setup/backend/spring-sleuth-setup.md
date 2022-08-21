# Spring sleuth setup
Spring Sleuth provides Spring Boot auto-configuration for distributed tracing. Skywalking integrates its micrometer so that it can send metrics to the Skywalking [Meter System](./../../concepts-and-designs/meter.md).

## Set up agent

1. Add micrometer and Skywalking meter registry dependency into the project's `pom.xml` file. You can find more details at [Toolkit micrometer](https://github.com/apache/skywalking-java/blob/20fb8c81b3da76ba6628d34c12d23d3d45c973ef/docs/en/setup/service-agent/java-agent/Application-toolkit-micrometer.md).
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-micrometer-registry</artifactId>
    <version>${skywalking.version}</version>
</dependency>
```

2. Create Skywalking meter registry in spring bean management.
```java
@Bean
SkywalkingMeterRegistry skywalkingMeterRegistry() {
    // Add rate configs If you need, otherwise using none args construct
    SkywalkingConfig config = new SkywalkingConfig(Arrays.asList(""));
    return new SkywalkingMeterRegistry(config);
}
```

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
