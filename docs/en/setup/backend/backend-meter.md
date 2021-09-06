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

## Meter collection

Metrics named in OAL script could be used in MAL, as described in [Official OAL script](../../guides/backend-oal-scripts.md)

Custom metrics may be collected by Manual Meter API. You may find correct APIs diving into [Server Agents](../service-agent/server-agents.md).
Custom metrics collected cannot be used directly, they should be configured in `meter-analyzer-config` configuration files, which is described in next part.

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

An example can be found [here](../../../../oap-server/server-starter/src/main/resources/meter-analyzer-config/spring-sleuth.yaml).
If you're using Spring Sleuth, see [Spring Sleuth Setup](spring-sleuth-setup.md).

| Rule Name | Description | Configuration File | Data Source |
|-----|-----|-----|-----|
|spring-sleuth| Metrics of Spring Sleuth Application | meter-analyzer-config/spring-sleuth.yaml | Sprign Sleuth Application --meter format--> SkyWalking OAP Server |

### Meters configuration

```yaml
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
