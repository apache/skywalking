# Zabbix Receiver
Zabbix receiver is accepting the metrics of [Zabbix Agent Active Checks protocol](https://www.zabbix.com/documentation/current/manual/appendix/items/activepassive#active_checks) format into the [Meter System](./../../concepts-and-designs/meter.md).
Zabbix Agent is base on GPL-2.0 License.

## Module define
```yaml
receiver-zabbix:
  selector: ${SW_RECEIVER_ZABBIX:default}
  default:
    # Export tcp port, Zabbix agent could connected and transport data
    port: 10051
    # Bind to host
    host: 0.0.0.0
    # Enable config when receive agent request
    activeFiles: agent
```

## Configuration file
Zabbix receiver is configured via a configuration file. The configuration file defines everything related to receiving 
 from agents, as well as which rule files to load.
 
OAP can load the configuration at bootstrap. If the new configuration is not well-formed, OAP fails to start up. The files
are located at `$CLASSPATH/zabbix-rules`.

The file is written in YAML format, defined by the scheme described below. Square brackets indicate that a parameter is optional.

An example for zabbix agent configuration could be found [here](../../../../test/e2e/e2e-test/docker/zabbix/zabbix_agentd.conf).
You could find the Zabbix agent detail items from [Zabbix Agent documentation](https://www.zabbix.com/documentation/current/manual/config/items/itemtypes/zabbix_agent).

### Configuration file

```yaml
# insert metricPrefix into metric name:  <metricPrefix>_<raw_metric_name>
metricPrefix: <string>
# expSuffix is appended to all expression in this file.
expSuffix: <string>
# Datasource from Zabbix Item keys.
requiredZabbixItemKeys:
 - <zabbix item keys>
# Support agent entities information.
entities:
  # Allow hostname patterns to build metrics.
  hostPatterns:
    - <regex string>
  # Customized metrics label before parse to meter system.
  labels:
    [- <labels> ]
# Metrics rule allow you to recompute queries.
metrics:
  [ - <metrics_rules> ]
```

#### <labels>

```yaml
# Define the label name. The label value must query from `value` or `fromItem` attribute.
name: <string>
# Appoint value to label.
[value: <string>]
# Query label value from Zabbix Agent Item key.
[fromItem: <string>]
```

#### <metric_rules>

```yaml
# The name of rule, which combinates with a prefix 'meter_' as the index/table name in storage.
name: <string>
# MAL expression.
exp: <string>
```

More about MAL, please refer to [mal.md](../../concepts-and-designs/mal.md).
