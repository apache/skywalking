# Log Analysis Language

Log Analysis Language (LAL) in SkyWalking is essentially a Domain-Specific Language (DSL) to analyze logs. You can use
LAL to parse, extract, and save the logs, as well as collaborate the logs with traces (by extracting the trace ID,
segment ID and span ID) and metrics (by generating metrics from the logs and sending them to the meter system).

The LAL config files are in YAML format, and are located under directory `lal`. You can
set `log-analyzer/default/lalFiles` in the `application.yml` file or set environment variable `SW_LOG_LAL_FILES` to
activate specific LAL config files.

## Layer
Layer should be declared in the LAL script to represent the analysis scope of the logs.

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

```
filter {
    if (log.service == "TestingService") { // Don't waste resources on TestingServices
        abort {} // all remaining components won't be executed at all
    }
    // ... parsers, extractors, sinks
}
```

Note that when you put `regexp` in an `if` statement, you need to surround the expression with `()`
like `regexp(<the expression>)`, instead of `regexp <the expression>`.

- `tag`

`tag` function provide a convenient way to get the value of a tag key.

We can add tags like following:
``` JSON
[
   {
      "tags":{
         "data":[
            {
               "key":"TEST_KEY",
               "value":"TEST_VALUE"
            }
         ]
      },
      "body":{
         ...
      }
      ...
   }
]
```
And we can use this method to get the value of the tag key `TEST_KEY`.
```
filter {
    if (tag("TEST_KEY") == "TEST_VALUE") {
         ...
    }
}
```

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

```
filter {
    json {
        abortOnFailure true // this is optional because it's default behaviour
    }
}
```

#### `yaml`

```
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

```
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

### Extractor

Extractors aim to extract metadata from the logs. The metadata can be a service name, a service instance name, an
endpoint name, or even a trace ID, all of which can be associated with the existing traces and metrics.

#### Standard fields

- `service`

`service` extracts the service name from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with traces / metrics.

- `instance`

`instance` extracts the service instance name from the `parsed` result, and set it into the `LogData`, which will be
persisted (if not dropped) and is used to associate with traces / metrics.

- `endpoint`

`endpoint` extracts the endpoint name from the `parsed` result, and set it into the `LogData`, which will be
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

The parameter of `timestamp` can be a millisecond:
```
filter {
    // ... parser

    extractor {
        timestamp parsed.time as String
    }
}
```
or a datetime string with a specified pattern:
```
filter {
    // ... parser

    extractor {
        timestamp parsed.time as String, "yyyy-MM-dd HH:mm:ss"
    }
}
```

- `layer`

`layer` extracts the [layer](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java) from the `parsed` result, and set it into the `LogData`, which will be persisted (if
not dropped) and is used to associate with service.

- `tag`

`tag` extracts the tags from the `parsed` result, and set them into the `LogData`. The form of this extractor should look something like this: `tag key1: value, key2: value2`. You may use the properties of `parsed` as both keys and values.

```
filter {
    // ... parser

    extractor {
        tag level: parsed.level, (parsed.statusCode): parsed.statusMsg
        tag anotherKey: "anotherConstantValue"
        layer 'GENERAL'
    }
}
```

#### Output fields

When a rule declares a custom `outputType` (see [Output Type](#output-type)), the extractor can set fields specific to
that output type. Any identifier in the extractor that is not a standard field (listed above) is treated as an
**output field assignment**. The syntax is the same as standard fields:

```
fieldName valueExpression as Type
```

The LAL compiler validates at boot time that a matching setter exists on the output type class (e.g., `setStatement(String)`
for field `statement`). If no setter is found, the OAP server will fail to start, ensuring early error detection.

See [Slow SQL](#slow-sql-database-slow-statement) and [Sampled Trace](#sampled-trace-network-profiling) for concrete examples.

#### `metrics`

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
    malFiles: ${SW_LOG_MAL_FILES:my-lal-mal-config, folder1/another-lal-mal-config, folder2/*} # files are under "log-mal-rules" directory
```

Examples are as follows:

```
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
    exp: http_response_time.sum(['le', 'service', 'instance']).increase('PT5M').histogram().histogram_percentile([50,75,90,95,99])
```

### Sink

Sinks are the persistent layer of the LAL. An explicit `sink {}` block is **required** for any data to be persisted.
Without a `sink {}` block, no data is saved — this applies to all LAL rules, including those using custom `outputType`.

Within the sink, you can use samplers, droppers, and enforcers to control which logs are persisted.
An empty `sink {}` block means all logs are saved unconditionally.

#### Sampler

Sampler allows you to save the logs in a sampling manner. Currently, the following sampling strategies are supported:

- `rateLimit`: samples `n` logs at a maximum rate of 1 minute. `rateLimit("SamplerID")` requires an ID for the sampler.
Sampler declarations with the same ID share the same sampler instance, thus sharing the same `rpm` and resetting logic.
- `possibility`: every piece of log has a pseudo possibility of `percentage` to be sampled, the possibility was generated by Java random number generator and compare to the given `percentage` option.

We welcome contributions on more sampling strategies. If multiple samplers are specified, the last one determines the
final sampling result. See examples in [Enforcer](#enforcer).

Examples 1, `rateLimit`:

```
filter {
    // ... parser

    sink {
        sampler {
            if (parsed.service == "ImportantApp") {
                rateLimit("ImportantAppSampler") {
                    rpm 1800  // samples at most 1800 logs per minute for service "ImportantApp"
                }
            } else {
                rateLimit("OtherSampler") {
                    rpm 180   // samples at most 180 logs per minute for other services than "ImportantApp"
                }
            }
        }
    }
}
```

Examples 2, `possibility`:

```
filter {
    // ... parser

    sink {
        sampler {
            if (parsed.service == "ImportantApp") {
                possibility(80) { // samples 80% of the logs for service "ImportantApp"
                }
            } else {
                possibility(30) { // samples 30% of the logs for other services than "ImportantApp"
                }
            }
        }
    }
}
```

#### Dropper

Dropper is a special sink, meaning that all logs are dropped without any exception. This is useful when you want to
drop debugging logs.

```
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

```
filter { // filter A: this is for persistence
    // ... parser

    sink {
        sampler {
            // .. sampler configs
        }
    }
}
filter { // filter B:
    // ... extractor to generate many metrics
    extractor {
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

```
filter {
    // ... parser

    sink {
        sampler {
            // ... sampler configs
        }
        if (parsed.level == "ERROR" || parsed.userId == "TestingUserId") { // sample error logs or testing users' logs (userId == "TestingUserId") even if the sampling strategy is configured
            enforcer {
            }
        }
    }
}
```

## Output Type

By default, each LAL rule produces a `Log` source object that is persisted to storage. However, some use cases require
transforming log data into a different entity type — for example, converting slow SQL logs into `DatabaseSlowStatement`
records or network profiling logs into `SampledTraceRecord`. The `outputType` mechanism makes this configurable per rule,
without requiring any DSL grammar changes.

### Configuration

Set `outputType` at the rule level in the YAML config. You can use the short name registered by
the `LALOutputBuilder` SPI (recommended), or a fully qualified class name:

```yaml
rules:
  - name: my-rule
    layer: MYSQL
    outputType: SlowSQL    # short name registered by DatabaseSlowStatementBuilder
    dsl: |
      filter {
        // ...
      }
```

### Resolution order

The output type is resolved per-rule in the following priority:

1. **Per-rule YAML config** — the `outputType` field shown above (highest priority).
   Short names (no `.`) are resolved via `ServiceLoader<LALOutputBuilder>`; fully qualified class names
   are resolved via `Class.forName()` as a fallback.
2. **`LALSourceTypeProvider` SPI** — a default output type registered by receiver plugins for a specific layer
3. **`Log.class`** — the fallback if not specified anywhere

### Two output paths

LAL supports two kinds of output types:

| Output path | Base type | How it works |
|---|---|---|
| **Log path** | Subclass of `AbstractLog` | The sink populates standard log fields (service, instance, endpoint, tags, body, etc.) from `LogData` and persists via `SourceReceiver` |
| **Builder path** | Implements `LALOutputBuilder` | The sink creates the builder, calls `init(LogData, NamingControl)` to pre-populate standard fields, applies output field values via setters, then calls `complete(SourceReceiver)` to validate and dispatch |

The builder path is used when the output type implements the `LALOutputBuilder` interface. This is how SkyWalking's
built-in slow SQL and sampled trace features work.

### Built-in output types

#### Slow SQL (Database Slow Statement)

SkyWalking converts slow SQL logs into `DatabaseSlowStatement` records for database slow query analysis (MySQL, PostgreSQL, Redis, etc.).

Use `outputType: SlowSQL` in your rule config.
The available output fields are: `id`, `statement`, `latency`. Standard fields (`service`, `layer`, `timestamp`) are handled
by the extractor as usual and pre-populated via `init()` from `LogData`.

We require a log tag `"LOG_KIND" = "SLOW_SQL"` to make OAP distinguish slow SQL logs from other log reports.

**Note**, slow SQL sampling would only flag this SQL in the candidate list. The OAP server would run statistic per service
and only persistent the top 50 every 10(controlled by `topNReportPeriod: ${SW_CORE_TOPN_REPORT_PERIOD:10}`) minutes by default.

See bundled LAL scripts for complete examples:
[mysql-slowsql.yaml](../../../oap-server/server-starter/src/main/resources/lal/mysql-slowsql.yaml),
[pgsql-slowsql.yaml](../../../oap-server/server-starter/src/main/resources/lal/pgsql-slowsql.yaml),
[redis-slowsql.yaml](../../../oap-server/server-starter/src/main/resources/lal/redis-slowsql.yaml).

#### Sampled Trace (Network Profiling)

SkyWalking converts network profiling sampled trace logs into `SampledTraceRecord` for process-level network analysis.

Use `outputType: SampledTrace` in your rule config.
The available output fields are: `latency`, `uri`, `reason`, `processId`, `destProcessId`, `detectPoint`, `componentId`.

We require a log tag `"LOG_KIND" = "NET_PROFILING_SAMPLED_TRACE"` to make OAP distinguish sampled trace logs from other log reports.

See bundled LAL scripts for complete examples:
[envoy-als.yaml](../../../oap-server/server-starter/src/main/resources/lal/envoy-als.yaml),
[k8s-service.yaml](../../../oap-server/server-starter/src/main/resources/lal/k8s-service.yaml),
[mesh-dp.yaml](../../../oap-server/server-starter/src/main/resources/lal/mesh-dp.yaml).

### Extending: custom output types

You can create custom output types to transform logs into any entity type for your own use cases. There are two approaches:

#### Approach 1: Implement `LALOutputBuilder`

Use this when you need full control over validation and dispatching, or when the output is not a simple `AbstractLog` subclass.

1. Create a class that implements `org.apache.skywalking.oap.server.core.source.LALOutputBuilder`:

```java
public class MyCustomBuilder implements LALOutputBuilder {
    public static final String NAME = "MyCustom";

    @Setter @Getter
    private String myField;
    @Setter @Getter
    private long myMetric;

    public MyCustomBuilder() {} // no-arg constructor required

    @Override
    public String name() { return NAME; }

    @Override
    public void init(LogData logData, NamingControl namingControl) {
        // Pre-populate from LogData (service, timestamp, traceId, etc.)
    }

    @Override
    public void complete(SourceReceiver sourceReceiver) {
        // Validate, build final Source/Record, and dispatch
    }
}
```

2. Register it as a `ServiceLoader` provider so the short name can be used in YAML config.
   Create `META-INF/services/org.apache.skywalking.oap.server.core.source.LALOutputBuilder` containing:
   ```
   com.example.MyCustomBuilder
   ```

3. Reference it in your LAL YAML config by short name:

```yaml
rules:
  - name: my-custom-rule
    layer: GENERAL
    outputType: MyCustom    # short name from name() method
    dsl: |
      filter {
        json {}
        extractor {
          myField parsed.someField as String
          myMetric parsed.someValue as Long
        }
        sink {}
      }
```

The LAL compiler validates at boot time that `setMyField(String)` and `setMyMetric(Long)` exist on `MyCustomBuilder`.
Fully qualified class names (e.g., `outputType: com.example.MyCustomBuilder`) are also supported as a fallback.

#### Approach 2: Extend `AbstractLog`

Use this for simpler cases where you want to produce a log-like record with additional fields:

1. Create a subclass of `AbstractLog` with the extra fields.
2. Set `outputType` to your subclass. Standard log fields are populated automatically.

#### Custom input types

For rules that receive structured protobuf data (not JSON/YAML log bodies), you can configure the input type
so that `parsed.*` generates optimized direct getter calls:

```yaml
rules:
  - name: my-proto-rule
    layer: MY_LAYER
    inputType: com.example.MyProtoMessage
    dsl: |
      filter {
        extractor {
          service parsed.serviceName as String
          endpoint parsed.requestPath as String
        }
      }
```

Alternatively, register a `LALSourceTypeProvider` SPI implementation to set the default input and output types
for an entire layer, so individual rules don't need to repeat the configuration:

```java
public class MyLayerSourceTypeProvider implements LALSourceTypeProvider {
    @Override
    public Layer layer() { return Layer.MY_LAYER; }

    @Override
    public Class<?> inputType() { return MyProtoMessage.class; }

    @Override
    public Class<? extends Source> outputType() { return MyCustomBuilder.class; }
}
```

Register it in `META-INF/services/org.apache.skywalking.oap.log.analyzer.v2.spi.LALSourceTypeProvider`.
