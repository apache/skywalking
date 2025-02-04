# Meter System -- Analysis Metrics and Meters
Meter system is a metric streaming process system, which focus on processing and analyzing aggregated metrics data.
Metrics from OpenTelemetry, Zabbix, Prometheus, SkyWalking meter APIs, etc., are all statistics, so they are processed
by the meter system.

# Meter Analysis Language

The meter system provides a functional analysis language called MAL (Meter Analysis Language) that lets users analyze and
aggregate meter data in the OAP streaming system. The result of an expression can either be ingested by the agent analyzer,
or the OpenTelemetry/Prometheus analyzer.

## Language data type

In MAL, an expression or sub-expression can evaluate to one of the following two types:

 - **Sample family**:  A set of samples (metrics) containing a range of metrics whose names are identical.
 - **Scalar**: A simple numeric value that supports integer/long and floating/double.

## Sample family

A set of samples, which acts as the basic unit in MAL. For example:

```
instance_trace_count
```

The sample family above may contain the following samples which are provided by external modules, such as the agent analyzer:

```
instance_trace_count{region="us-west",az="az-1"} 100
instance_trace_count{region="us-east",az="az-3"} 20
instance_trace_count{region="asia-north",az="az-1"} 33
```

### Tag filter

MAL supports four type operations to filter samples in a sample family by tag:

 - tagEqual: Filter tags exactly equal to the string provided.
 - tagNotEqual: Filter tags not equal to the string provided.
 - tagMatch: Filter tags that regex-match the string provided.
 - tagNotMatch: Filter labels that do not regex-match the string provided.

For example, this filters all instance_trace_count samples for us-west and asia-north region and az-1 az:

```
instance_trace_count.tagMatch("region", "us-west|asia-north").tagEqual("az", "az-1")
```
### Value filter

MAL supports six type operations to filter samples in a sample family by value:

- valueEqual: Filter values exactly equal to the value provided.
- valueNotEqual: Filter values equal to the value provided.
- valueGreater: Filter values greater than the value provided.
- valueGreaterEqual: Filter values greater than or equal to the value provided.
- valueLess: Filter values less than the value provided.
- valueLessEqual: Filter values less than or equal to the value provided.

For example, this filters all instance_trace_count samples for values >= 33:

```
instance_trace_count.valueGreaterEqual(33)
```
### Tag manipulator
MAL allows tag manipulators to change (i.e. add/delete/update) tags and their values.

#### K8s
MAL supports using the metadata of K8s to manipulate the tags and their values.
This feature requires authorizing the OAP Server to access K8s's `API Server`.

##### retagByK8sMeta
`retagByK8sMeta(newLabelName, K8sRetagType, existingLabelName, namespaceLabelName)`. Add a new tag to the sample family based on the value of an existing label. Provide several internal converting types, including
- K8sRetagType.Pod2Service

Add a tag to the sample using `service` as the key, `$serviceName.$namespace` as the value, and according to the given value of the tag key, which represents the name of a pod.

For example:
```
container_cpu_usage_seconds_total{namespace=default, container=my-nginx, cpu=total, pod=my-nginx-5dc4865748-mbczh} 2
```
Expression:
```
container_cpu_usage_seconds_total.retagByK8sMeta('service' , K8sRetagType.Pod2Service , 'pod' , 'namespace')
```
Output:
```
container_cpu_usage_seconds_total{namespace=default, container=my-nginx, cpu=total, pod=my-nginx-5dc4865748-mbczh, service='nginx-service.default'} 2
```

### Binary operators

The following binary arithmetic operators are available in MAL:

 - \+ (addition)
 - \- (subtraction)
 - \* (multiplication)
 - / (division)

Binary operators are defined between scalar/scalar, sampleFamily/scalar and sampleFamily/sampleFamily value pairs.

Between two scalars: they evaluate to another scalar that is the result of the operator being applied to both scalar operands:

```
1 + 2
```

Between a sample family and a scalar, the operator is applied to the value of every sample in the sample family. For example:

```
instance_trace_count + 2
```

or

```
2 + instance_trace_count
```

results in

```
instance_trace_count{region="us-west",az="az-1"} 102 // 100 + 2
instance_trace_count{region="us-east",az="az-3"} 22 // 20 + 2
instance_trace_count{region="asia-north",az="az-1"} 35 // 33 + 2
```

Between two sample families, a binary operator is applied to each sample in the sample family on the left and
its matching sample in the sample family on the right. A new sample family with empty name will be generated.
Only the matched tags will be reserved. Samples with no matching samples in the sample family on the right will not be found in the result.

Another sample family `instance_trace_analysis_error_count` is

```
instance_trace_analysis_error_count{region="us-west",az="az-1"} 20
instance_trace_analysis_error_count{region="asia-north",az="az-1"} 11
```

Example expression:

```
instance_trace_analysis_error_count / instance_trace_count
```

This returns a resulting sample family containing the error rate of trace analysis. Samples with region us-west and az az-3
have no match and will not show up in the result:

```
{region="us-west",az="az-1"} 0.2  // 20 / 100
{region="asia-north",az="az-1"} 0.3333  // 11 / 33
```

### Aggregation Operation

Sample family supports the following aggregation operations that can be used to aggregate the samples of a single sample family,
resulting in a new sample family having fewer samples (sometimes having just a single sample) with aggregated values:

 - sum (calculate sum over dimensions)
 - min (select minimum over dimensions)
 - max (select maximum over dimensions)
 - avg (calculate the average over dimensions)
 - count (calculate the count over dimensions, the last tag will be counted)

These operations can be used to aggregate overall label dimensions or preserve distinct dimensions by inputting `by` parameter( the keyword `by` could be omitted)

```
<aggr-op>(by=[<tag1>, <tag2>, ...])
```

Example expression:

```
instance_trace_count.sum(by=['az'])
```

will output the following result:

```
instance_trace_count{az="az-1"} 133 // 100 + 33
instance_trace_count{az="az-3"} 20
```

___
**Note, aggregation operations affect the samples from one bulk only. If the metrics are reported parallel from multiple instances/nodes
through different SampleFamily, this aggregation would NOT work.**

In the best practice for this scenario, build the metric with labels that represent each instance/node. Then use the 
[AggregateLabels Operation in MQE](../api/metrics-query-expression.md#aggregatelabels-operation) to aggregate the metrics.
___

### Function

`Duration` is a textual representation of a time range. The formats accepted are based on the ISO-8601 duration format {@code PnDTnHnMn.nS}
 where a day is regarded as exactly 24 hours.

Examples:
 - "PT20.345S" -- parses as "20.345 seconds"
 - "PT15M"     -- parses as "15 minutes" (where a minute is 60 seconds)
 - "PT10H"     -- parses as "10 hours" (where an hour is 3600 seconds)
 - "P2D"       -- parses as "2 days" (where a day is 24 hours or 86400 seconds)
 - "P2DT3H4M"  -- parses as "2 days, 3 hours and 4 minutes"
 - "P-6H3M"    -- parses as "-6 hours and +3 minutes"
 - "-P6H3M"    -- parses as "-6 hours and -3 minutes"
 - "-P-6H+3M"  -- parses as "+6 hours and -3 minutes"

#### increase
`increase(Duration)`: Calculates the increase in the time range.

#### rate
`rate(Duration)`: Calculates the per-second average rate of increase in the time range.

#### irate
`irate()`: Calculates the per-second instant rate of increase in the time range.

#### tag
`tag({allTags -> })`: Updates tags of samples. User can add, drop, rename and update tags.

#### histogram
`histogram(le: '<the tag name of le>')`: Transforms less-based histogram buckets to meter system histogram buckets.
`le` parameter represents the tag name of the bucket.

**Note** In SkyWalking, the histogram buckets are based on time and will be transformed to the `milliseconds-based`
histogram buckets in the meter system. (If the metrics from the Prometheus are based on the `seconds-based` histogram
buckets, will multiply the bucket value by 1000.)

#### histogram_percentile
`histogram_percentile([<p scalar>])`: Represents the meter-system to calculate the p-percentile (0 ≤ p ≤ 100) from the buckets.

#### time
`time()`: Returns the number of seconds since January 1, 1970 UTC.

#### foreach
`forEach([string_array], Closure<Void> each)`: Iterates all samples according to the first array argument, and provide two parameters in the second closure argument:
1. `element`: element in the array.
2. `tags`: tags in each sample.

## Down Sampling Operation
MAL should instruct meter-system on how to downsample for metrics. It doesn't only refer to aggregate raw samples to
`minute` level, but also expresses data from `minute` in higher levels, such as `hour` and `day`.

Down sampling function is called `downsampling` in MAL, and it accepts the following types:

 - AVG
 - SUM
 - LATEST
 - SUM_PER_MIN
 - MIN
 - MAX
 - MEAN (TODO)
 - COUNT (TODO)

The default type is `AVG`.

If users want to get the latest time from `last_server_state_sync_time_in_seconds`:

```
last_server_state_sync_time_in_seconds.tagEqual('production', 'catalog').downsampling(LATEST)
```

## Metric level function

They extract level relevant labels from metric labels, then informs the meter-system the level and [layer](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/Layer.java) to which this metric belongs.

 - `service([svc_label1, svc_label2...], Layer)` extracts service level labels from the array argument, extracts layer from `Layer` argument.
 - `instance([svc_label1, svc_label2...], [ins_label1, ins_label2...], Layer, Closure<Map<String, String>> propertiesExtractor)` extracts service level labels from the first array argument,
                                                                        extracts instance level labels from the second array argument, extracts layer from `Layer` argument, `propertiesExtractor` is an optional closure that extracts instance properties from `tags`, e.g. `{ tags -> ['pod': tags.pod, 'namespace': tags.namespace] }`.
 - `endpoint([svc_label1, svc_label2...], [ep_label1, ep_label2...])` extracts service level labels from the first array argument,
                                                                      extracts endpoint level labels from the second array argument, extracts layer from `Layer` argument.
 - `process([svc_label1, svc_label2...], [ins_label1, ins_label2...], [ps_label1, ps_label2...], layer_lable)` extracts service level labels from the first array argument,
                                                                      extracts instance level labels from the second array argument, extracts process level labels from the third array argument, extracts layer label from fourse argument.
 - `serviceRelation(DetectPoint, [source_svc_label1...], [dest_svc_label1...], Layer)` DetectPoint including `DetectPoint.CLIENT` and `DetectPoint.SERVER`,
   extracts `sourceService` labels from the first array argument, extracts `destService` labels from the second array argument, extracts layer from `Layer` argument.
 - `processRelation(detect_point_label, [service_label1...], [instance_label1...], source_process_id_label, dest_process_id_label, component_label)` extracts `DetectPoint` labels from first argument, the label value should be `client` or `server`.
   extracts `Service` labels from the first array argument, extracts `Instance` labels from the second array argument, extracts `ProcessID` labels from the fourth and fifth arguments of the source and destination.

## Decorate function
`decorate({ me -> me.attr0 = ...})`: Decorate the [MeterEntity](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/analysis/meter/MeterEntity.java) with additional attributes. 
The closure takes the MeterEntity as an argument. This function is used to add additional attributes to the metrics. More details, see [Metrics Additional Attributes](metrics-additional-attributes.md).

## Configuration file

The OAP can load the configuration at bootstrap. If the new configuration is not well-formed, the OAP fails to start up. The files
are located at `$CLASSPATH/otel-rules`, `$CLASSPATH/meter-analyzer-config`, `$CLASSPATH/envoy-metrics-rules` and `$CLASSPATH/zabbix-rules`.

The file is written in YAML format, defined by the scheme described below. Brackets indicate that a parameter is optional.

A full example can be found [here](../../../oap-server/server-starter/src/main/resources/otel-rules/oap.yaml)

Generic placeholders are defined as follows:

* `<string>`: A regular string.
* `<closure>`: A closure with custom logic.

```yaml
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

### <metric_rules>

```yaml
# The name of rule, which combinates with a prefix 'meter_' as the index/table name in storage.
name: <string>
# MAL expression.
exp: <string>
```

## More Examples

Please refer to [OAP Self-Observability](../../../oap-server/server-starter/src/main/resources/otel-rules/oap.yaml).
