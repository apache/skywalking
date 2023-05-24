# Meter receiver
The meter receiver accepts the metrics of [meter protocol](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Meter.proto) into the [meter system](./../../concepts-and-designs/meter.md).

## Module definition
Module definition is defined in `application.yml`, typically located at `$SKYWALKING_BASE_DIR/config/application.yml` by default.
```yaml
receiver-meter:
  selector: ${SW_RECEIVER_METER:default}
  default:

```

In Kafka Fetcher, follow these configurations to enable it.  
```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
```

## Report Meter Telemetry Data

### Manual Meter API

Custom metrics may be collected by the Manual Meter API.
Custom metrics collected cannot be used directly; they should be configured in the `meter-analyzer-config` configuration files described in the next part.

The receiver adds labels with `key = service` and `key = instance` to the collected data samples,
and values from service and service instance name defined in SkyWalking Agent,
for identification of the metric data.

A typical manual meter API set is [Spring MicroMeter Observations APIs](micrometer-observations.md)

### OpenTelemetry Exporter

You can use OpenTelemetry Collector to transport the metrics to SkyWalking OAP.
Read the doc on [Skywalking Exporter](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/exporter/skywalkingexporter/README.md) for a detailed guide.

The following is the correspondence between the OpenTelemetry Metric Data Type and the Skywalking Data Collect Protocol: 

| OpenTelemetry Metric Data Type | Skywalking Data Collect Protocol |
|-----|-----|
|MetricDataTypeGauge| MeterSingleValue |
|MetricDataTypeSum| MeterSingleValue |
|MetricDataTypeHistogram| MeterHistogram and two MeterSingleValues containing `$name_sum` and `$name_count` metrics. |
|MetricDataTypeSummary| A series of MeterSingleValue containing tag `quantile` and two MeterSingleValues containing `$name_sum` and `$name_count` metrics. |
|MetricDataTypeExponentialHistogram| Not Supported|

Note: `$name` is the original metric name.

## Configuration file
The meter receiver is configured via a configuration file. The configuration file defines everything related to receiving 
 from agents, as well as which rule files to load.

The OAP can load the configuration at bootstrap. If the new configuration is not well-formed, the OAP may fail to start up. The files
are located at `$CLASSPATH/meter-analyzer-config`.

New meter-analyzer-config files is **NOT** enabled by default, you should make meter configuration take effect through section `agent-analyzer` in `application.yml` of skywalking backend.
```yaml
 agent-analyzer:
   selector: ${SW_AGENT_ANALYZER:default}
   default:
     # ... take care of other analyzers
     meterAnalyzerActiveFiles: ${SW_METER_ANALYZER_ACTIVE_FILES:your-custom-meter-conf-without-ext-name} # The multiple files should be separated by ","
```

Meter-analyzer-config file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

All available meter analysis scripts could be found [here](../../../../oap-server/server-starter/src/main/resources/meter-analyzer-config/).

| Rule Name | Description | Configuration File | Data Source |
|-----|-----|-----|-----|
|satellite| Metrics of SkyWalking Satellite self-observability(so11y)| meter-analyzer-config/satellite.yaml| SkyWalking Satellite --meter format-->SkyWalking OAP Server|
|threadpool| Metrics of Thread Pool | meter-analyzer-config/threadpool.yaml | Thread Pool --meter format--> SkyWalking OAP Server |
|datasource| Metrics of DataSource metrics | meter-analyzer-config/datasource.yaml | Datasource --meter format--> SkyWalking OAP Server |
|spring-micrometer| Metrics of Spring Sleuth Application | meter-analyzer-config/spring-micrometer.yaml | Spring Sleuth Application --meter format--> SkyWalking OAP Server |

An example can be found [here](../../../../oap-server/server-starter/src/main/resources/meter-analyzer-config/spring-micrometer.yaml).
If you're using Spring MicroMeter Observations, see [Spring MicroMeter Observations APIs](micrometer-observations.md).

### Meters configuration

```yaml
# initExp is the expression that initializes the current configuration file
initExp: <string>
# filter the metrics, only those metrics that satisfy this condition will be passed into the `metricsRules` below.
filter: <closure> # example: '{ tags -> tags.job_name == "vm-monitoring" }'
# expPrefix is executed before the metrics executes other functions.
expPrefix: <string>
# expSuffix is appended to all expression in this file.
expSuffix: <string>
# insert metricPrefix into metric name:  <metricPrefix>_<raw_metric_name>
metricPrefix: <string>
# Metrics rule allow you to recompute queries.
metricsRules:
  # The name of rule, which combinates with a prefix '<metricPrefix>_' as the index/table name in storage.
  # The name with prefix can also be quoted in UI (Dashboard/Template/Item/Metrics)
  name: <string>
  # MAL expression. Raw name of custom metrics collected can be used here
  exp: <string>
```

For more information on MAL, please refer to [mal.md](../../concepts-and-designs/mal.md)

#### `rate`, `irate`, and `increase`

Although we support the `rate`, `irate`, `increase` functions in the backend, we still recommend users to consider using client-side APIs to run these functions. The reasons are as follows:
1. The OAP has to set up caches to calculate the values.
1. Once the agent reconnects to another OAP instance, the time windows of rate calculation break. This leads to inaccurate results.
