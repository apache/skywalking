# Meter Analysis Language

The meter system provides a functional analysis language called MAL (Meter Analysis Language) that lets users analyze and 
aggregate meter data in the OAP streaming system. The result of an expression can either be ingested by the agent analyzer,
or the OC/Prometheus analyzer.

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

MAL supports four type operations to filter samples in a sample family:

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
{region="us-west",az="az-1"} 0.8  // 20 / 100
{region="asia-north",az="az-1"} 0.3333  // 11 / 33
```

### Aggregation Operation

Sample family supports the following aggregation operations that can be used to aggregate the samples of a single sample family,
resulting in a new sample family having fewer samples (sometimes having just a single sample) with aggregated values:

 - sum (calculate sum over dimensions)
 - min (select minimum over dimensions)
 - max (select maximum over dimensions)
 - avg (calculate the average over dimensions)
 
These operations can be used to aggregate overall label dimensions or preserve distinct dimensions by inputting `by` parameter. 

```
<aggr-op>(by: <tag1, tag2, ...>)
```

Example expression:

```
instance_trace_count.sum(by: ['az'])
```

will output the following result:

```
instance_trace_count{az="az-1"} 133 // 100 + 33
instance_trace_count{az="az-3"} 20
```

### Function

`Duraton` is a textual representation of a time range. The formats accepted are based on the ISO-8601 duration format {@code PnDTnHnMn.nS}
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

#### histogram_percentile
`histogram_percentile([<p scalar>])`. Represents the meter-system to calculate the p-percentile (0 ≤ p ≤ 100) from the buckets. 

#### time
`time()`: Returns the number of seconds since January 1, 1970 UTC.


## Down Sampling Operation
MAL should instruct meter-system on how to downsample for metrics. It doesn't only refer to aggregate raw samples to 
`minute` level, but also expresses data from `minute` in higher levels, such as `hour` and `day`. 

Down sampling function is called `downsampling` in MAL, and it accepts the following types:

 - AVG
 - SUM
 - LATEST
 - MIN (TODO)
 - MAX (TODO)
 - MEAN (TODO)
 - COUNT (TODO)

The default type is `AVG`.

If users want to get the latest time from `last_server_state_sync_time_in_seconds`:

```
last_server_state_sync_time_in_seconds.tagEqual('production', 'catalog').downsampling(LATEST)
```

## Metric level function

There are three levels in metric: service, instance and endpoint. They extract level relevant labels from metric labels, then informs the meter-system the level to which this metric belongs.

 - `servcie([svc_label1, svc_label2...])` extracts service level labels from the array argument.
 - `instance([svc_label1, svc_label2...], [ins_label1, ins_label2...])` extracts service level labels from the first array argument, 
                                                                        extracts instance level labels from the second array argument.
 - `endpoint([svc_label1, svc_label2...], [ep_label1, ep_label2...])` extracts service level labels from the first array argument, 
                                                                      extracts endpoint level labels from the second array argument.

## More Examples

Please refer to [OAP Self-Observability](../../../oap-server/server-bootstrap/src/main/resources/fetcher-prom-rules/self.yaml)
