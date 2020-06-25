# Meter Receiver
Meter receiver is accept meter from [meter protocol](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Meter.proto) into the [Meter System](./../../concepts-and-designs/meter.md).o

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
      # Appoint the endpoint name if using ENDPOINT scope
      endpoint: <string>
    # The agent source of the transformation operation.
    meter:
      # The transformation operation from prometheus metrics to Skywalking ones. 
      operation: <string>
      # Meter value parse groovy script.
      value: <string>
      # Appoint percentiles if using avgHistogramPercentile operation.
      percentile:
        - <rank>
```

#### Meter transform operation

The available operations are `avg`, `avgHistogram` and `avgHistogramPercentile`. The `avg` and `avgXXX` mean to average
the raw received metrics. 

When you specify `avgHistogram` and `avgHistogramPercentile`, the source should be the type of `histogram`.

#### Meter value script

The script is provide a easy way to custom build a complex value, and it also support combine multiple meter into one.

Using `meter[""]` to get one meter from all of the meter witch received at ***per agent***, also there have multiple build-in method to help filter or operate the value.
It will combine the values and collect into the Meter System.

|Function|Support meter type|Description|
|------|-------|------|
|tagFilter(String, String)|Counter,Gauge,Histogram|Filter tag key/value from meter|
|add(double)|Counter,Gauge|Add value into meter|
|add(Meter)|Counter,Gauge|Add value from a meter value|
|reduce(double)|Counter,Gauge|Reduce value into meter|
|reduce(Meter)|Counter,Gauge|Reduce value from a meter value|
|multiply(double)|Counter,Gauge|Multiply value into meter|
|multiply(Meter)|Counter,Gauge|Multiply value form a meter value|
|mean(double)|Counter,Gauge|Mean value into meter|
|mean(Meter)|Counter,Gauge|Mean value from a meter value|
|scale(int)|Counter,Gauge|Scale the meter value|
|rate(string)|Counter,Gauge,Histogram|Rate value from the time range|
|irate(string)|Counter,Gauge,Histogram|IRate value from the time range|
|increase(string)|Counter,Gauge,Histogram|Increase value from the time range|

The `add`, `reduce`, `multiply`, `mean` with Meter only support operate from single Meter value, you could using `tagFilter` to filter, Because we could not operation between two multiple values.

If you want to using `rate`, `irate`, `increase` function, I suggest to let it rate counter at the agent side. 
Because if the connection between agent and backend has reconnected, the time windows will be break, so we cannot find previous value from the window.