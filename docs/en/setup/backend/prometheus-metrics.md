# Prometheus Fetcher
Prometheus fetcher reads metrics from the Prometheus endpoint and transfers the metrics into SkyWalking native format for the MAL engine.

## Configuration file
Prometheus fetcher is configured via a configuration file. The configuration file defines everything related to fetching
services and their instances, as well as which rule files to load.

The OAP can load the configuration at bootstrap. If the new configuration is not well-formed, the OAP fails to start up. The files
are located at `$CLASSPATH/fetcher-prom-rules`.

The file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

A full example can be found [here](../../../../oap-server/server-starter/src/main/resources/fetcher-prom-rules/self.yaml)

Generic placeholders are defined as follows:

* `<duration>`: This is parsed into a textual representation of a duration. The accepted formats are based on
  the ISO-8601 duration format `PnDTnHnMn.nS` with days of exactly 24 hours.
* `<labelname>`: A string matching the regular expression \[a-zA-Z_\]\[a-zA-Z0-9_\]*.
* `<labelvalue>`: A string of Unicode characters.
* `<host>`: A valid string consisting of a hostname or IP followed by an optional port number.
* `<path>`: A valid URL path.
* `<string>`: A regular string.

```yaml
# How frequently to fetch targets.
fetcherInterval: <duration>
# Per-fetch timeout when fetching this target.
fetcherTimeout: <duration>
# The HTTP resource path on which to fetch metrics from targets.
metricsPath: <path>
#Statically configured targets.
staticConfig:
  # The targets specified by the static config.
  targets:
    [ - <target> ]
  # Labels assigned to all metrics fetched from the targets.
  labels:
    [ <labelname>: <labelvalue> ... ]
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
   [ - <metric_rules> ]
```

### <target>

```yaml
# The url of target exporter. the format should be complied with "java.net.URI"
url: <string>
# The path of root CA file.
sslCaFilePath: <string>
```

### <metric_rules>

```yaml
# The name of rule, which combinates with a prefix 'meter_' as the index/table name in storage.
name: <string>
# MAL expression.
exp: <string>
```

To know more about MAL, please refer to [mal.md](../../concepts-and-designs/mal.md)

## Active Fetcher Rules
Suppose you want to enable some `metric-custom.yaml` files stored at `fetcher-prom-rules`, append its name to `enabledRules` of
`prometheus-fetcher` as follows:

```yaml
prometheus-fetcher:
  selector: ${SW_PROMETHEUS_FETCHER:default}
  default:
    enabledRules: ${SW_PROMETHEUS_FETCHER_ENABLED_RULES:"self,metric-custom"}
```
