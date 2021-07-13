# Log Analysis Language

Log Analysis Language (LAL) in SkyWalking is essentially a Domain-Specific Language (DSL) to analyze logs. You can use
LAL to parse, extract, and save the logs, as well as collaborate the logs with traces (by extracting the trace ID,
segment ID and span ID) and metrics (by generating metrics from the logs and sending them to the meter system).

The LAL config files are in YAML format, and are located under directory `lal`. You can
set `log-analyzer/default/lalFiles` in the `application.yml` file or set environment variable `SW_LOG_LAL_FILES` to
activate specific LAL config files.

## Filter

A filter is a group of [parser](#parser), [extractor](#extractor) and [sink](#sink). Users can use one or more filters
to organize their processing logic. Every piece of log will be sent to all filters in an LAL rule. A piece of log
sent to the filter is available as property `log` in the LAL, therefore you can access the log service name
via `log.service`. For all available fields of `log`, please refer to [the protocol definition](https://github.com/apache/skywalking-data-collect-protocol/blob/master/logging/Logging.proto#L41).

All components are executed sequentially in the orders they are declared.

### Global Functions

Globally available functions may be used them in all components (i.e. parsers, extractors, and sinks) where necessary.

- `abort`

By default, all components declared are executed no matter what flags (`dropped`, `saved`, etc.) have been set. There
are cases where you may want the filter chain to stop earlier when specified conditions are met. `abort` function aborts
the remaining filter chain from where it's declared, and all the remaining components won't be executed at all.
`abort` function serves as a fast-fail mechanism in LAL.

```groovy
filter {
    if (log.service == "TestingService") { // Don't waste resources on TestingServices
        abort {} // all remaining components won't be executed at all
    }
    // ... parsers, extractors, sinks
}
```

Note that when you put `regexp` in an `if` statement, you need to surround the expression with `()`
like `regexp(<the expression>)`, instead of `regexp <the expression>`.

### Parser

Parsers are responsible for parsing the raw logs into structured data in SkyWalking for further processing. There are 3
types of parsers at the moment, namely `json`, `yaml`, and `text`.

When a piece of log is parsed, there is a corresponding property available, called `parsed`, injected by LAL.
Property `parsed` is typically a map, containing all the fields parsed from the raw logs. For example, if the parser
is `json` / `yaml`, `parsed` is a map containing all the key-values in the `json` / `yaml`; if the parser is `text`
, `parsed` is a map containing all the captured groups and their values (for `regexp` and `grok`).

All parsers share the following options:

| Option | Type | Description | Default Value |
| ------ | ---- | ----------- | ------------- |
| `abortOnFailure` | `boolean` | Whether the filter chain should abort if the parser failed to parse / match the logs | `true` |

See examples below.

#### `json`

```groovy
filter {
    json {
        abortOnFailure true // this is optional because it's default behaviour
    }
}
```

#### `yaml`

```groovy
filter {
    yaml {
        abortOnFailure true // this is optional because it's default behaviour
    }
}
```

#### `text`

For unstructured logs, there are some `text` parsers for use.

- `regexp`

`regexp` parser uses a regular expression (`regexp`) to parse the logs. It leverages the captured groups of the regexp,
all the captured groups can be used later in the extractors or sinks.
`regexp` returns a `boolean` indicating whether the log matches the pattern or not.

```groovy
filter {
    text {
        abortOnFailure true // this is optional because it's default behaviour
        // this is just a demo pattern
        regexp "(?<timestamp>\\d{8}) (?<thread>\\w+) (?<level>\\w+) (?<traceId>\\w+) (?<msg>.+)"
    }
    extractor {
        tag level: parsed.level
        // we add a tag called `level` and its value is parsed.level, captured from the regexp above
        traceId parsed.traceId
        // we also extract the trace id from the parsed result, which will be used to associate the log with the trace
    }
    // ...
}
```

- `grok` (TODO)

We're aware of certains performance issues in the grok Java library, and so we're currently conducting investigations and benchmarking. Contributions are
welcome.

### Extractor

Extractors aim to extract metadata from the logs. The metadata can be a service name, a service instance name, an
endpoint name, or even a trace ID, all of which can be associated with the existing traces and metrics.

- `service`

`service` extracts the service name from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

- `instance`

`instance` extracts the service instance name from the `parsed` result, and set it into the `LogData`, which will be
persisted (if not dropped) and is used to associate with traces / metrics.

- `endpoint`

`endpoint` extracts the service instance name from the `parsed` result, and set it into the `LogData`, which will be
persisted (if not dropped) and is used to associate with traces / metrics.

- `traceId`

`traceId` extracts the trace ID from the `parsed` result, and set it into the `LogData`, which will be persisted (if not
dropped) and is used to associate with traces / metrics.

- `segmentId`

`segmentId` extracts the segment ID from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

- `spanId`

`spanId` extracts the span ID from the `parsed` result, and set it into the `LogData`, which will be persisted (if not
dropped) and is used to associate with traces / metrics.

- `timestamp`

`timestamp` extracts the timestamp from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

The unit of `timestamp` is millisecond.

- `tag`

`tag` extracts the tags from the `parsed` result, and set them into the `LogData`. The form of this extractor should look something like this: `tag key1: value, key2: value2`. You may use the properties of `parsed` as both keys and values.

```groovy
filter {
    // ... parser

    extractor {
        tag level: parsed.level, (parsed.statusCode): parsed.statusMsg
        tag anotherKey: "anotherConstantValue"
    }
}
```

- `metrics`

`metrics` extracts / generates metrics from the logs, and sends the generated metrics to the meter system. You may
configure [MAL](mal.md) for further analysis of these metrics. The dedicated MAL config files are under
directory `log-mal-rules`, and you can set `log-analyzer/default/malFiles` to enable configured files.

```yaml
# application.yml
# ...
log-analyzer:
  selector: ${SW_LOG_ANALYZER:default}
  default:
    lalFiles: ${SW_LOG_LAL_FILES:my-lal-config} # files are under "lal" directory
    malFiles: ${SW_LOG_MAL_FILES:my-lal-mal-config,another-lal-mal-config} # files are under "log-mal-rules" directory
```

Examples are as follows:

```groovy
filter {
    // ...
    extractor {
        service parsed.serviceName
        metrics {
            name "log_count"
            timestamp parsed.timestamp
            labels level: parsed.level, service: parsed.service, instance: parsed.instance
            value 1
        }
        metrics {
            name "http_response_time"
            timestamp parsed.timestamp
            labels status_code: parsed.statusCode, service: parsed.service, instance: parsed.instance
            value parsed.duration
        }
    }
    // ...
}
```

The extractor above generates a metrics named `log_count`, with tag key `level` and value `1`. After that, you can
configure MAL rules to calculate the log count grouping by logging level like this:

```yaml
# ... other configurations of MAL

metrics:
  - name: log_count_debug
    exp: log_count.tagEqual('level', 'DEBUG').sum(['service', 'instance']).increase('PT1M')
  - name: log_count_error
    exp: log_count.tagEqual('level', 'ERROR').sum(['service', 'instance']).increase('PT1M')

```

The other metrics generated is `http_response_time`, so you can configure MAL rules to generate more useful metrics
like percentiles.

```yaml
# ... other configurations of MAL

metrics:
  - name: response_time_percentile
    exp: http_response_time.sum(['le', 'service', 'instance']).increase('PT5M').histogram().histogram_percentile([50,70,90,99])
```

### Sink

Sinks are the persistent layer of the LAL. By default, all the logs of each filter are persisted into the storage.
However, some mechanisms allow you to selectively save some logs, or even drop all the logs after you've
extracted useful information, such as metrics.

#### Sampler

Sampler allows you to save the logs in a sampling manner. Currently, the sampling strategy `rateLimit` is supported. We welcome
contributions on more sampling strategies. If multiple samplers are specified, the last one determines the final sampling
result. See examples in [Enforcer](#enforcer).

`rateLimit` samples `n` logs at a maximum rate of 1 second. `rateLimit("SamplerID")` requires an ID for the sampler. Sampler
declarations with the same ID share the same sampler instance, thus sharing the same `qps` and resetting logic.

Examples:

```groovy
filter {
    // ... parser

    sink {
        sampler {
            if (parsed.service == "ImportantApp") {
                rateLimit("ImportantAppSampler") {
                    qps 30  // samples 30 pieces of logs every second for service "ImportantApp"
                }
            } else {
                rateLimit("OtherSampler") {
                    qps 3   // samples 3 pieces of logs every second for other services than "ImportantApp"
                }
            }
        }
    }
}
```

#### Dropper

Dropper is a special sink, meaning that all logs are dropped without any exception. This is useful when you want to
drop debugging logs.

```groovy
filter {
    // ... parser

    sink {
        if (parsed.level == "DEBUG") {
            dropper {}
        } else {
            sampler {
                // ... configs
            }
        }
    }
}
```

Or if you have multiple filters, some of which are for extracting metrics, only one of them has to be persisted.

```groovy
filter { // filter A: this is for persistence
    // ... parser

    sink {
        sampler {
            // .. sampler configs
        }
    }
}
filter { // filter B:
    // ... extractors to generate many metrics
    extractors {
        metrics {
            // ... metrics
        }
    }
    sink {
        dropper {} // drop all logs because they have been saved in "filter A" above.
    }
}
```

#### Enforcer

Enforcer is another special sink that forcibly samples the log. A typical use case of enforcer is when you have
configured a sampler and want to save some logs forcibly, such as to save error logs even if the sampling mechanism
has been configured.

```groovy
filter {
    // ... parser

    sink {
        sampler {
            // ... sampler configs
        }
        if (parserd.level == "ERROR" || parsed.userId == "TestingUserId") { // sample error logs or testing users' logs (userId == "TestingUserId") even if the sampling strategy is configured
            enforcer {
            }
        }
    }
}
```

You can use `enforcer` and `dropper` to simulate a probabilistic sampler like this.

```groovy
filter {
    // ... parser

    sink {
        sampler { // simulate a probabilistic sampler with sampler rate 30% (not accurate though)
            if (Math.abs(Math.random()) > 0.3) {
                enforcer {}
            } else {
                dropper {}
            }
        }
    }
}
```
