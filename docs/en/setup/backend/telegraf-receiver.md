# Telegraf receiver

The Telegraf receiver supports receiving InfluxDB Telegraf's metrics by meter-system. 
The OAP can load the configuration at bootstrap. The files are located at `$CLASSPATH/telegraf-rules`.
If the new configuration is not well-formed, the OAP may fail to start up.

This is the [InfluxDB Telegraf](https://docs.influxdata.com/telegraf/v1.24/) Document, 
the Telegraf receiver can handle Telegraf's [CPU Input Plugin](https://github.com/influxdata/telegraf/blob/release-1.24/plugins/inputs/cpu/README.md), 
[Memory Input Plugin](https://github.com/influxdata/telegraf/blob/release-1.24/plugins/inputs/mem/README.md).

There are many other telegraf input plugins, users can customize different input plugins' rule files.
The rule file should be in YAML format, defined by the scheme described in [MAL](../../concepts-and-designs/mal.md).
Please see the [telegraf plugin directory](https://docs.influxdata.com/telegraf/v1.24/plugins/) for more input plugins information.

**Notice:**
* The Telegraf receiver module uses `HTTP` to receive telegraf's metrics, 
so the outputs method should be set `[[outputs.http]]` in telegraf.conf file.
Please see the [http outputs](https://github.com/influxdata/telegraf/blob/release-1.24/plugins/outputs/http/README.md)
for more details.

* The Telegraf receiver module **only** process telegraf's `JSON` metrics format,
the data format should be set `data_format = "json"` in telegraf.conf file.
Please see the [JSON data format](https://docs.influxdata.com/telegraf/v1.24/data_formats/output/json/)
for more details.

* The default `json_timestamp_units` is second in JSON output, 
and the Telegraf receiver module **only** process `second` timestamp unit.
If users configure `json_timestamp_units` in telegraf.conf file, `json_timestamp_units = "1s"` is feasible.
Please see the [JSON data format](https://docs.influxdata.com/telegraf/v1.24/data_formats/output/json/)
for more details.

The following is the default telegraf receiver YAML rule file in the `application.yml`,
Set `SW_RECEIVER_TELEGRAF:default` through system environment or change `SW_RECEIVER_TELEGRAF_ACTIVE_FILES:vm`
to activate the OpenTelemetry receiver with `vm.yml` in telegraf-rules.
```yaml
receiver-telegraf:
  selector: ${SW_RECEIVER_TELEGRAF:default}
  default:
    activeFiles: ${SW_RECEIVER_TELEGRAF_ACTIVE_FILES:vm}
```

| Rule Name | Description    | Configuration File     | Data Source                                                             |
|-----------|----------------|------------------------|-------------------------------------------------------------------------|
| vm        | Metrics of VMs | telegraf-rules/vm.yaml | Telegraf inputs plugins --> Telegraf Receiver --> SkyWalking OAP Server |

