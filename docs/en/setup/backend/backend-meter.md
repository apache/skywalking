# Meter Receiver
Meter receiver is accepting the metrics of [meter protocol](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Meter.proto) format into the [Meter System](./../../concepts-and-designs/meter.md).

## Module define
receiver-meter:
  selector: ${SW_RECEIVER_METER:default}
  default:

## Configuration file
Meter receiver is configured via a configuration file. The configuration file defines everything related to receiving 
 from agents, as well as which rule files to load.
 
OAP can load the configuration at bootstrap. If the new configuration is not well-formed, OAP fails to start up. The files
are located at `$CLASSPATH/meter-receive-config`.

The file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

A example can be found [here](../../../../oap-server/server-bootstrap/src/main/resources/meter-receive-config/meter-receive-config.yaml)

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
      # <Optional> Appoint percentiles if using avgHistogramPercentile operation.
      percentile:
        - <rank>
```

#### Meter transform operation

The available operations are `avg`, `avgHistogram` and `avgHistogramPercentile`. The `avg` and `avgXXX` mean to average
the raw received metrics. 

When you specify `avgHistogram` and `avgHistogramPercentile`, the source should be the type of `histogram`.

#### Meter value script

The script is provide a easy way to custom build a complex value, and it also support combine multiple meter into one.

Use `meter[""]` to refer the metrics raw data, and multiple build-in methods to help filter or operate the value.

|Function|Support meter type|Description|
|------|-------|------|
|tagFilter(String, String)|Counter,Gauge,Histogram|Filter tag key/value from meter|
|add(double)|SingleValue|Add value into meter|
|add(Meter)|SingleValue|Add value from a meter value|
|subtract(double)|SingleValue|Reduce value into meter|
|subtract(Meter)|SingleValue|Reduce value from a meter value|
|multiply(double)|SingleValue|Multiply value into meter|
|multiply(Meter)|SingleValue|Multiply value form a meter value|
|divide(double)|SingleValue|Mean value into meter|
|divide(Meter)|SingleValue|Mean value from a meter value|
|scale(int)|SingleValue|Scale the meter value|
|rate(string)|SingleValue,Histogram|Rate value from the time range|
|irate(string)|SingleValue,Histogram|IRate value from the time range|
|increase(string)|SingleValue,Histogram|Increase value from the time range|

The `add`, `minus`, `multiply`, and `divide` support single Meter value. Use `tagFilter` to filter.

If you want to use `rate`, `irate`, `increase` function, use client-side API.
- FAQ, why no `rate`, `irate`, `increase` at the backend.
Once the agent reconnected to another OAP instance, the time windows of rate calculation will break. Then, the result would not be accurate.