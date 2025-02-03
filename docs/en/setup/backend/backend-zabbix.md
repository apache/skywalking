# Zabbix Receiver
The Zabbix receiver accepts metrics of [Zabbix Agent Active Checks protocol](https://www.zabbix.com/documentation/current/manual/appendix/items/activepassive#active_checks) format into the [Meter System](./../../concepts-and-designs/mal.md).
Zabbix Agent is based on GPL-2.0 License, only version `6.x` and below are supported.

## Module definition
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
The Zabbix receiver is configured via a configuration file that defines everything related to receiving 
 from agents, as well as which rule files to load.
 
The OAP can load the configuration at bootstrap. If the new configuration is not well-formed, the OAP fails to start up. The files
are located at `$CLASSPATH/zabbix-rules`.

The file is written in YAML format, defined by the scheme described below. Square brackets indicate that a parameter is optional.

An example for Zabbix agent configuration could be found [here](../../../../test/e2e-v2/cases/vm/zabbix/zabbix_agentd.conf).
You can find details on Zabbix agent items from [Zabbix Agent documentation](https://www.zabbix.com/documentation/current/manual/config/items/itemtypes/zabbix_agent).

### Configuration file

```yaml
# initExp is the expression that initializes the current configuration file
initExp: <string>
# insert metricPrefix into metric name:  <metricPrefix>_<raw_metric_name>
metricPrefix: <string>
# expPrefix is executed before the metrics executes other functions.
expPrefix: <string>
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

For more on MAL, please refer to [mal.md](../../concepts-and-designs/mal.md).
