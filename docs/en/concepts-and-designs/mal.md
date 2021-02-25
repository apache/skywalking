# Meter Analysis Language

Meter system provides a functional analysis language called MAL(Meter Analysis Language) that lets the user analyze and 
aggregate meter data in OAP streaming system. The result of an expression can either be ingested by agent analyzer,
or OC/Prometheus analyzer.

## Language data type

In MAL, an expression or sub-expression can evaluate to one of two types:

 - Sample family -  a set of samples(metrics) containing a range of metrics whose name is identical.
 - Scalar - a simple numeric value. it supports integer/long, floating/double,

## Sample family

A set of samples, which is as the basic unit in MAL. For example:

```
instance_trace_count
```

The above sample family might contains following simples which are provided by external modules, for instance, agent analyzer:

```
instance_trace_count{region="us-west",az="az-1"} 100
instance_trace_count{region="us-east",az="az-3"} 20
instance_trace_count{region="asia-north",az="az-1"} 33
```

### Tag filter

MAL support four type operations to filter samples in a sample family:

 - tagEqual: Filter tags that are exactly equal to the provided string.
 - tagNotEqual: Filter tags that are not equal to the provided string.
 - tagMatch: Filter tags that regex-match the provided string.
 - tagNotMatch: Filter labels that do not regex-match the provided string.

For example, this filters all instance_trace_count samples for us-west and asia-north region and az-1 az:

```
instance_trace_count.tagMatch("region", "us-west|asia-north").tagEqual("az", "az-1")
```

### Binary operators

The following binary arithmetic operators are available in MAL:

 - \+ (addition)
 - \- (subtraction)
 - \* (multiplication)
 - / (division)

Binary operators are defined between scalar/scalar, sampleFamily/scalar and sampleFamily/sampleFamily value pairs.

Between two scalars: they evaluate to another scalar that is the result of the operator applied to both scalar operands:

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

Between two sample families, a binary operator is applied to each sample in the left-hand side sample family and 
its matching sample in the right-hand sample family. A new sample family with empty name will be generated.
Only the matched tags will be reserved. Samples for which no matching sample in the right-hand sample family are not in the result.

Another sample family `instance_trace_analysis_error_count` is 

```
instance_trace_analysis_error_count{region="us-west",az="az-1"} 20
instance_trace_analysis_error_count{region="asia-north",az="az-1"} 11 
```

Example expression:

```
instance_trace_analysis_error_count / instance_trace_count
```

This returns a result sample family containing the error rate of trace analysis. The samples with region us-west and az az-3 
have no match and will not show up in the result:

```
{region="us-west",az="az-1"} 0.8  // 20 / 100
{region="asia-north",az="az-1"} 0.3333  // 11 / 33
```

### Aggregation Operation

Sample family supports the following aggregation operations that can be used to aggregate the samples of a single sample family,
resulting in a new sample family of fewer samples(even single one) with aggregated values:

 - sum (calculate sum over dimensions)
 - min (select minimum over dimensions)
 - max (select maximum over dimensions)
 - avg (calculate the average over dimensions)
 
These operations can be used to aggregate over all label dimensions or preserve distinct dimensions by inputting `by` parameter. 

```
<aggr-op>(by: <tag1, tag2, ...>)
```

Example expression:

```
instance_trace_count.sum(by: ['az'])
```

will output a result:

```
instance_trace_count{az="az-1"} 133 // 100 + 33
instance_trace_count{az="az-3"} 20
```

### Function

`Duraton` is a textual representation of a time range. The formats accepted are based on the ISO-8601 duration format {@code PnDTnHnMn.nS}
 with days considered to be exactly 24 hours.

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
`increase(Duration)`. Calculates the increase in the time range.

#### rate
`rate(Duration)`. Calculates the per-second average rate of increase of the time range.

#### irate
`irate()`. Calculates the per-second instant rate of increase of the time range.

#### tag
`tag({allTags -> })`. Update tags of samples. User can add, drop, rename and update tags.

#### histogram
`histogram(le: '<the tag name of le>')`. Transforms less based histogram buckets to meter system histogram buckets. 
`le` parameter hints the tag name of a bucket. 

#### histogram_percentile
`histogram_percentile([<p scalar>])`. Hints meter-system to calculates the p-percentile (0 ≤ p ≤ 100) from the buckets. 

#### time
`time()`. returns the number of seconds since January 1, 1970 UTC.

## Down Sampling Operation
MAL should instruct meter-system how to do downsampling for metrics. It doesn't only refer to aggregate raw samples to 
`minute` level, but also hints data from `minute` to higher levels, for instance, `hour` and `day`. 

Down sampling function is called `downsampling` in MAL, it accepts the following types:

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

Metric has three level, service, instance and endpoint. They extract level relevant labels from metric labels, then
 hints meter-system which level this metrics should be.

 - `servcie([svc_label1, svc_label2...])` extracts service level labels from the array argument.
 - `instance([svc_label1, svc_label2...], [ins_label1, ins_label2...])` extracts service level labels from the first array argument, 
                                                                        extracts instance level labels from the second array argument.
 - `endpoint([svc_label1, svc_label2...], [ep_label1, ep_label2...])` extracts service level labels from the first array argument, 
                                                                      extracts endpoint level labels from the second array argument.

## More Examples

Please refer to [OAP Self-Observability](../../../oap-server/server-bootstrap/src/main/resources/fetcher-prom-rules/self.yaml)
