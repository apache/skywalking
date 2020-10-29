# Meter Receiver
Meter receiver is accepting the metrics of [meter protocol](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Meter.proto) format into the [Meter System](./../../concepts-and-designs/meter.md).

## Module define
```yaml
receiver-meter:
  selector: ${SW_RECEIVER_METER:default}
  default:

```

In Kafka Fetcher, we need to follow the configuration to enable it.  
```yaml
kafka-fetcher:
  selector: ${SW_KAFKA_FETCHER:default}
  default:
    bootstrapServers: ${SW_KAFKA_FETCHER_SERVERS:localhost:9092}
    enableMeterSystem: ${SW_KAFKA_FETCHER_ENABLE_METER_SYSTEM:true}
```

## Configuration file
Meter receiver is configured via a configuration file. The configuration file defines everything related to receiving 
 from agents, as well as which rule files to load.
 
OAP can load the configuration at bootstrap. If the new configuration is not well-formed, OAP fails to start up. The files
are located at `$CLASSPATH/meter-analyzer-config`.

The file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

A example can be found [here](../../../../oap-server/server-bootstrap/src/main/resources/meter-analyzer-config/spring-sleuth.yaml).
If you're using Spring sleuth, you could use [Spring Sleuth Setup](spring-sleuth-setup.md).

### Meters configure

```yaml
# Meter config allow your to recompute
meters:
  # Meter name which combines with a prefix 'meter_' as the index/table name in storage.
  - name: <string>
    # The meter scope
    scope:
      # Scope type should be one of SERVICE, SERVICE_INSTANCE, ENDPOINT
      type: <string>
      # <Optional> Appoint the endpoint name if using ENDPOINT scope
      endpoint: <string>
    # The agent source of the transformation operation.
    meter:
      # The transformation operation from prometheus metrics to Skywalking ones. 
      operation: <string>
      # Meter value parse groovy script.
      value: <string>
      # Aggregate metrics group by dedicated labels
      groupBy:
        - <labelName>
      # <Optional> Appoint percentiles if using avgHistogramPercentile operation.
      percentile:
        - <rank>
```

#### Meter transform operation

The available operations are `avg`, `avgLabeled`, `avgHistogram` and `avgHistogramPercentile`. The `avg` and `avgXXX` mean to average
the raw received metrics. 

When you specify `avgHistogram` and `avgHistogramPercentile`, the source should be the type of `histogram`.

#### Meter value script

The script is provide a easy way to custom build a complex value, and it also support combine multiple meter into one.

##### Meter value grammar
```
// Declare the meter value.
meter[METER_NAME]
[.tagFilter(TAG_KEY, TAG_VALUE)]
.FUNCTION(VALUE | METER)
```
##### Meter Name

Use name to refer the metrics raw data from agent side.

##### Tag Filter

Use the meter tag to filter the meter value.
> meter["test_meter"].tagFilter("k1", "v1")

In this case, filter the tag key equals `k1` and tag value equals `v1` value from `test_meter`.

##### Aggregation Function

Use multiple build-in methods to help operate the value.

Provided functions
- `add`. Add value into meter. Support single value.
> meter["test_meter"].add(2)

In this case, all of the meter values will add `2`.
> meter["test_meter1"].add(meter["test_meter2"])

In this case, all of the `test_meter1` values will add value from `test_meter2`, ensure `test_meter2` only has single value to operate, could use `tagFilter`.
- `subtract`. Subtract value into meter. Support single value.
> meter["test_meter"].subtract(2)

In this case, all of the meter values will subtract `2`.
> meter["test_meter1"].subtract(meter["test_meter2"])

In this case, all of the `test_meter1` values will subtract value from `test_meter2`, ensure `test_meter2` only has single value to operate, could use `tagFilter`.
- `multiply`. Multiply value into meter. Support single value.
> meter["test_meter"].multiply(2)

In this case, all of the meter values will multiply `2`.
> meter["test_meter1"].multiply(meter["test_meter2"])

In this case, all of the `test_meter1` values will multiply value from `test_meter2`, ensure `test_meter2` only has single value to operate, could use `tagFilter`.
- `divide`. Divide value into meter. Support single value.
> meter["test_meter"].divide(2)

In this case, all of the meter values will divide `2`.
> meter["test_meter1"].divide(meter["test_meter2"])

In this case, all of the `test_meter1` values will divide value from `test_meter2`, ensure `test_meter2` only has single value to operate, could use `tagFilter`.
- `scale`. Scale value into meter. Support single value.
> meter["test_meter"].scale(2)

In this case, all of the meter values will scale `2`. For example, `meter["test_meter"]` value is 1, then using `scale(2)`, the result will be `100`.
- `rate`.(Not Recommended) Rate value from the time range. Support single value and Histogram.
> meter["test_meter"].rate("P15S")

In this case, all of the meter values will rate from `15s` before.
- `irate`.(Not Recommended) IRate value from the time range. Support single value and Histogram.
> meter["test_meter"].irate("P15S")

In this case, all of the meter values will irate from `15s` before.
- `increase`.(Not Recommended) increase value from the time range. Support single value and Histogram.
> meter["test_meter"].increase("P15S")

In this case, all of the meter values will increase from `15s` before.

Even we supported `rate`, `irate`, `increase` function in the backend, but we still recommend user to consider using client-side APIs to do these. Because
1. The OAP has to set up caches to calculate the value.
1. Once the agent reconnected to another OAP instance, the time windows of rate calculation will break. Then, the result would not be accurate.