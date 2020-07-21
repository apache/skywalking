# Open Fetcher
Fetcher is a concept in SkyWalking backend. It uses pulling mode rather than [receiver](backend-receivers.md), which
read the data from the target systems. This mode is typically in some metrics SDKs, such as Prometheus.

## Prometheus Fetcher
```yaml
prometheus-fetcher:
  selector: ${SW_PROMETHEUS_FETCHER:default}
  default:
    active: ${SW_PROMETHEUS_FETCHER_ACTIVE:false}
``` 

### Configuration file
Prometheus fetcher is configured via a configuration file. The configuration file defines everything related to fetching
 services and their instances, as well as which rule files to load.
                   
OAP can load the configuration at bootstrap. If the new configuration is not well-formed, OAP fails to start up. The files
are located at `$CLASSPATH/fetcher-prom-rules`.

The file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

A full example can be found [here](../../../../oap-server/server-bootstrap/src/main/resources/fetcher-prom-rules/self.yaml)

Generic placeholders are defined as follows:

 * `<duration>`: a duration This will parse a textual representation of a duration. The formats accepted are based on 
                 the ISO-8601 duration format `PnDTnHnMn.nS` with days considered to be exactly 24 hours.
 * `<labelname>`: a string matching the regular expression [a-zA-Z_][a-zA-Z0-9_]*
 * `<labelvalue>`: a string of unicode characters
 * `<host>`: a valid string consisting of a hostname or IP followed by an optional port number
 * `<path>`: a valid URL path
 * `<string>`: a regular string

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
    [ - <host> ]
  # Labels assigned to all metrics fetched from the targets.
  labels:
    [ <labelname>: <labelvalue> ... ]
# Metrics rule allow you to recompute queries.
metricsRules:
   [ - <metric_rules> ]
```

#### <metric_rules>

```yaml
# The name of rule, which combinates with a prefix 'meter_' as the index/table name in storage.
name: <string>
# Scope should be one of SERVICE, INSTANCE and ENDPOINT.
scope: <string>
# The transformation operation from prometheus metrics to skywalking ones. 
operation: <operation>
# The prometheus sources of the transformation operation.
sources:
  # The prometheus metric family name 
  <string>:
    # Function for counter, one of INCREASE, RATE, and IRATE.
    [counterFunction: <string> ]
    # The range of a counterFunction.
    [range: <duration>]
    # The percentile rank of percentile operation
    [percentiles: [<rank>,...]]
    # Relabel prometheus labels to skywalking dimensions.
    relabel:
      service: [<labelname>, ...]
      [instance: [<labelname>, ...]]
      [endpoint: [<labelname>, ...]]
```

#### <operation>

The available operations are `avg`, `avgHistogram` and `avgHistogramPercentile`. The `avg` and `avgXXX` mean to average
the raw fetched metrics or high rate metrics into low rate metrics. The process is the extension of skywalking downsampling, 
that adds the procedure from raw data to minute rate.

When you specify `avgHistogram` and `avgHistogramPercentile`, the source should be the type of `histogram`. A counterFunction
is also needed due to the `bucket`, `sum` and `count` of histogram are counters.
