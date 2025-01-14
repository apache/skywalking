# Metrics Query Expression(MQE) Syntax
MQE is a string that consists of one or more expressions. Each expression could be a combination of one or more operations. 
The expression allows users to do simple query-stage calculation through [V3 APIs](./query-protocol.md#v3-apis).    

```text
Expression = <Operation> Expression1 <Operation> Expression2 <Operation> Expression3 ...
```

The following document lists the operations supported by MQE.

## Metrics Expression
Metrics Expression will return a collection of time-series values.

### Common Value Metrics
Expression:
```text
<metric_name>
```

For example: 
If we want to query the `service_sla` metric, we can use the following expression:
```text
service_sla
```

#### Result Type
The `ExpressionResultType` of the expression is `TIME_SERIES_VALUES`.

### Labeled Value Metrics
Since v10.0.0, SkyWalking supports multiple labels metrics.
We could query the specific labels of the metric by the following expression.

Expression:
```text
<metric_name>{<label1_name>='<label1_value_1>,...', <label2_name>='<label2_value_1>,...',<label2...}
```
`{<label1_name>='<label_value_1>,...'}` is the selected label name/value of the metric. If is not specified, all label values of the metric will be selected. 

For example:
The `k8s_cluster_deployment_status` metric has labels `namespace`, `deployment` and `status`.
If we want to query all deployment metric value with `namespace=skywalking-showcase` and `status=true`, we can use the following expression:
```text
k8s_cluster_deployment_status{namespace='skywalking-showcase', status='true'}
```

We also could query the label with multiple values by separating the values with `,`:
If we want to query the `service_percentile` metric with the label name `p` and values `50,75,90,95,99`, we can use the following expression:
```text
service_percentile{p='50,75,90,95,99'}
```
If we want to rename the label values to `P50,P75,P90,P95,P99`, see [Relabel Operation](#relabel-operation).

#### Result Type
The `ExpressionResultType` of the expression is `TIME_SERIES_VALUES` and with labels.

## Binary Operation 
The Binary Operation is an operation that takes two expressions and performs a calculation on their results.
The following table lists the binary operations supported by MQE.

Expression:
```text
Expression1 <Binary-Operator> Expression2
```

| Operator | Definition           |
|----------|----------------------|
| +        | addition             |
| -        | subtraction          |
| *        | multiplication       |
| /        | division             |
| %        | modulo               |

For example:
If we want to transform the service_sla metric value to percent, we can use the following expression:
```text
service_sla / 100
```

### Result Type
For the result type of the expression, please refer to the following table.

### Binary Operation Rules
The following table lists if the different result types of the input expressions could do this operation and the result type after the operation.
The expression could be on the left or right side of the operator. 
**Note**: If the expressions result on both sides of the operator are `with labels`, they should have the same labels for calculation.
If the labels match, will reserve left expression result labels and the calculated value. Otherwise, will return empty value.

| Expression              | Expression                | Yes/No | ExpressionResultType     |
|-------------------------|---------------------------|--------|--------------------------|
| SINGLE_VALUE            | SINGLE_VALUE              | Yes    | SINGLE_VALUE             |
| SINGLE_VALUE            | TIME_SERIES_VALUES        | Yes    | TIME_SERIES_VALUES       |
| SINGLE_VALUE            | SORTED_LIST/RECORD_LIST   | Yes    | SORTED_LIST/RECORD_LIST  |
| TIME_SERIES_VALUES      | TIME_SERIES_VALUES        | Yes    | TIME_SERIES_VALUES       |
| TIME_SERIES_VALUES      | SORTED_LIST/RECORD_LIST   | no     |                          |
| SORTED_LIST/RECORD_LIST | SORTED_LIST/RECORD_LIST   | no     |                          |

## Compare Operation
Compare Operation takes two expressions and compares their results.
The following table lists the compare operations supported by MQE.

Expression:
```text
Expression1 <Compare-Operator> Expression2
```

| Operator | Definition            |
|----------|-----------------------|
| \>       | greater than          |
| \>=      | greater than or equal |
| <        | less than             |
| <=       | less than or equal    |
| ==       | equal                 |
| !=       | not equal             |

The result of the compare operation is an **int value**:
* 1: true
* 0: false

For example:
Compare the `service_resp_time` metric value if greater than 3000, if the `service_resp_time` result is:
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": []
          },
          "values": [{"id": "1691658000000", "value": "2500", "traceID": null}, {"id": "1691661600000", "value": 3500, "traceID": null}]
        }
      ]
    }
  }
}
```
we can use the following expression:
```text
service_resp_time > 3000
```
and get result:
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": []
          },
          "values": [{"id": "1691658000000", "value": "0", "traceID": null}, {"id": "1691661600000", "value": 1, "traceID": null}]
        }
      ]
    }
  }
}
```

### Compare Operation Rules and Result Type
Same as the [Binary Operation Rules](#binary-operation-rules).

## Bool Operation
Bool Operation takes two `compare` expressions and performs a logical operation on their results.
The following table lists the bool operations supported by MQE.

Expression:
```text
Compare Expression1 <Bool-Operator> Expression2
```
**Notice**: The `Bool-Operator` only supports the `compare` expressions, which means the result of the left and right expressions should be `Compare Operation Result`.

| Operator | Definition  |
|----------|-------------|
| &&       | logical AND |
| \|\|     | logical OR  |

For example:
If we want to query the `service_resp_time` metric value greater than 3000 and `service_cpm` less than 1000, we can use the following expression:

```text
service_resp_time > 3000 && service_cpm < 1000
```

## Aggregation Operation
Aggregation Operation takes an expression and performs aggregate calculations on its results.

Expression:
```text
<Aggregation-Operator>(Expression)
```

| Operator | Definition                                        | ExpressionResultType |
|----------|---------------------------------------------------|----------------------|
| avg      | average the result                                | SINGLE_VALUE         |
| count    | count number of the result                        | SINGLE_VALUE         |
| latest   | select the latest non-null value from the result  | SINGLE_VALUE         |
| sum      | sum the result                                    | SINGLE_VALUE         |
| max      | select maximum from the result                    | SINGLE_VALUE         |
| min      | select minimum from the result                    | SINGLE_VALUE         |

For example:
If we want to query the average value of the `service_cpm` metric, we can use the following expression:

```text
avg(service_cpm)
```

### Result Type
The different operators could impact the `ExpressionResultType`, please refer to the above table.

## Mathematical Operation
Mathematical Operation takes an expression and performs mathematical calculations on its results.

Expression:
```text
<Mathematical-Operator>(Expression, parameters)
```

| Operator | Definition                                                                | parameters                                                         | ExpressionResultType          |
|----------|---------------------------------------------------------------------------|--------------------------------------------------------------------|-------------------------------|
| abs      | returns the absolute value of the result                                  |                                                                    | follow the input expression |
| ceil     | returns the smallest integer value that is greater or equal to the result |                                                                    | follow the input expression |
| floor    | returns the largest integer value that is greater or equal to the result  |                                                                    | follow the input expression |
| round    | returns result round to specific decimal places                           | `places`: a positive integer specific decimal places of the result | follow the input expression |

For example:
If we want to query the average value of the `service_cpm` metric in seconds, 
and round the result to 2 decimal places, we can use the following expression:

```text
round(service_cpm / 60 , 2)
```

### Result Type
The different operators could impact the `ExpressionResultType`, please refer to the above table.

## TopN Operation
### TopN Query
TopN Operation takes an expression and performs calculation to get the TopN of Services/Instances/Endpoints.
The result depends on the `entity` condition in the query.
- Global TopN: 
  - The `entity` is empty.
  - The result is the topN Services/Instances/Endpoints in the whole traffics. 
  - **Notice**: If query the Endpoints metric, the global candidate set could be huge, please use it carefully. 
- Service's Instances/Endpoints TopN: 
  - The `serviceName` in the `entity` is not empty.
  - The result is the topN Instances/Endpoints of the service.

Expression:
```text
top_n(<metric_name>, <top_number>, <order>, <attrs>)
```

- `top_number` is the number of the top results, should be a positive integer.
- `order` is the order of the top results. The value of `order` can be `asc` or `des`. 
- `attrs` optional, attrs is the attributes of the metrics, could be used to filter the topN results. 
   SkyWalking supports 6 attrs: `attr0`, `attr1`, `attr2`, `attr3`, `attr4`, `attr5`. 
   The format is `attr0='value', attr1='value'...attr5='value5'`, could use one or multiple attrs to filter the topN results.
   The attrs filter also supports not-equal filter `!=`, the format is `attr0 != 'value'`.

**Notice**: 
- The `attrs` should be added in the metrics first, see [Metrics Additional Attributes](../concepts-and-designs/metrics-additional-attributes.md).
- When use not-equal filter, for example `attr1 != 'value'`, if the storage is using `MySQL` or other JDBC storage and `attr1 value is NULL` in the metrics, 
the result of `attr1 != 'value'` will always `false` and would NOT include this metric in the result due to SQL can't compare `NULL` with the `value`.

For example:
1. If we want to query the top 10 services with the highest `service_cpm` metric value, we can use the following expression and make sure the `entity` is empty:
```text
top_n(service_cpm, 10, des)
```
If we want to filter the result by `Layer`, we can use the following expression:
```text
top_n(service_cpm, 10, des, attr0='GENERAL')
```

2. If we want to query the current service's top 10 instances with the highest `service_instance_cpm` metric value, we can use the following expression
under specific service:

```text
top_n(service_instance_cpm, 10, des)
```

### Result Type
According to the type of the metric, the `ExpressionResultType` of the expression will be `SORTED_LIST` or `RECORD_LIST`.

### Multiple TopNs Merging
As the difference between agent and ebpf, some metrics would be separated, e.g. service cpm and k8s service cpm.
If you want to merge the topN results of these metrics, you can use the `ton_n_of` operation to merge the results. 

expression:
```text
ton_n_of(<top_n>, <top_n>, ...,<top_number>, <order>)
```

- `<top_n>` is the [topN](#topn-query) expression. The result type of those tonN expression should be same, can be `SORTED_LIST` or `RECORD_LIST`, `but can not be mixed`.
- `<top_number>` is the number of the merged top results, should be a positive integer. 
- `<order>` is the order of the merged top results. The value of `<order>` can be `asc` or `des`.

for example:
If we want to get the top 10 services with the highest `service_cpm` and `k8s_service_cpm`, we can use the following expression:
```text
ton_n_of(top_n(service_cpm, 10, des), top_n(k8s_service_cpm, 10, des), 10, des)
```

## Relabel Operation
Relabel Operation takes an expression and replaces the label with new label on its results.
Since v10.0.0, SkyWalking supports relabel multiple labels.

Expression:
```text
relabel(Expression, <target_label_name>='<origin_label_value_1>,...', <new_label_name>='<new_label_value_1>,...')
```

The order of the new label values should be the same as the order of the label values in the input expression result.

For example:
If we want to query the `service_percentile` metric with the label values `50,75,90,95,99`, and rename the label name to `percentile` and the label values to `P50,P75,P90,P95,P99`, we can use the following expression:

```text
relabel(service_percentile{p='50,75,90,95,99'}, p='50,75,90,95,99', percentile='P50,P75,P90,P95,P99')
```

### Result Type
Follow the input expression.

## AggregateLabels Operation
AggregateLabels Operation takes an expression and performs an aggregate calculation on its `Labeled Value Metrics` results. It aggregates a group of `TIME_SERIES_VALUES` into a single `TIME_SERIES_VALUES`.

Expression:
```text
aggregate_labels(Expression, <AggregateType>(<label1_name>,<label2_name>...))
```

- `AggregateType` is the type of the aggregation operation.
- `<label1_name>,<label2_name>...` is the label names that need to be aggregated. If not specified, all labels will be aggregated. Optional.

| AggregateType | Definition                                         | ExpressionResultType |
|---------------|----------------------------------------------------|----------------------|
| avg           | calculate avg value of a `Labeled Value Metrics`   | TIME_SERIES_VALUES   |
| sum           | calculate sum value of a `Labeled Value Metrics`   | TIME_SERIES_VALUES   |
| max           | select the maximum value from a `Labeled Value Metrics` | TIME_SERIES_VALUES   |
| min           | select the minimum value from a `Labeled Value Metrics` | TIME_SERIES_VALUES   |

For example:
If we want to query all Redis command total rates, we can use the following expression(`total_commands_rate` is a metric which recorded every command rate in labeled value):
Aggregating all the labels:
```text
aggregate_labels(total_commands_rate, sum)
```
Also, we can aggregate by the `cmd` label:

```text
aggregate_labels(total_commands_rate, sum(cmd))
```

### Result Type
The ExpressionResultType of the aggregateLabels operation is TIME_SERIES_VALUES.

## Logical Operation
### ViewAsSequence Operation
ViewAsSequence operation represents the first not-null metric from the listing metrics in the given prioritized sequence(left to right). It could also be considered as a `short-circuit` of given metrics for the first value existing metric.

Expression:
```text
view_as_seq([<expression_1>, <expression_2>, ...])
```

For example:
if the first expression value is empty but the second one is not empty, it would return the result from the second expression. 
The following example would return the content of the **service_cpm** metric.

```text
view_as_seq(not_existing, service_cpm)
```

#### Result Type
The result type is determined by the type of selected not-null metric expression.

### IsPresent Operation
IsPresent operation represents that in a list of metrics, if any expression has a value, it would return `1` in the result; otherwise, it would return `0`.

Expression:
```text
is_present([<expression_1>, <expression_2>, ...])
```

For example:
When the meter does not exist or the metrics has no value, it would return `0`. 
However, if the metrics list contains meter with values, it would return `1`.
```text
is_present(not_existing, existing_without_value, existing_with_value)
```

#### Result Type
The result type is `SINGLE_VALUE`, and the result(`1` or `0`) in the first value.

## Trend Operation
Trend Operation takes an expression and performs a trend calculation on its results.

Expression:
```text
<Trend-Operator>(Metrics Expression, time_range)
```

`time_range` is the positive int of the calculated range. The unit will automatically align with to the query [Step](../../../oap-server/server-core/src/main/java/org/apache/skywalking/oap/server/core/query/enumeration/Step.java),
for example, if the query Step is `MINUTE`, the unit of `time_range` is `minute`.


| Operator | Definition                                                                            | ExpressionResultType          |
|----------|---------------------------------------------------------------------------------------|-------------------------------|
| increase | returns the increase in the time range in the time series                             | TIME_SERIES_VALUES |
| rate     | returns the per-second average rate of increase in the time range in the time series  | TIME_SERIES_VALUES |

For example:
If we want to query the increase value of the `service_cpm` metric in 2 minute(assume the query Step is MINUTE),
we can use the following expression:

```text
increase(service_cpm, 2)
```

If the query duration is 3 minutes, from (T1 to T3) and the metric has values in time series:
```text
V(T1-2), V(T1-1), V(T1), V(T2), V(T3)
```
then the expression result is:
```text
V(T1)-V(T1-2), V(T2)-V(T1-1), V(T3)-V(T1)
```

**Note**:
* If the calculated metric value is empty, the result will be empty. Assume in the T3 point, the increase value = V(T3)-V(T1), If the metric V(T3) or V(T1) is empty, the result value in T3 will be empty.

### Result Type
TIME_SERIES_VALUES.

## Sort Operation
### SortValues Operation
SortValues Operation takes an expression and sorts the values of the input expression result.

Expression:
```text
sort_values(Expression, <limit>, <order>)
```
- `limit` is the number of the sort results, should be a positive integer, if not specified, will return all results. Optional.
- `order` is the order of the sort results. The value of `order` can be `asc` or `des`.

For example:
If we want to sort the `service_resp_time` metric values in descending order and get the top 10 values, we can use the following expression:
```text
sort_values(service_resp_time, 10, des)
```

#### Result Type
The result type follows the input expression.

### SortLabelValues Operation
SortLabelValues Operation takes an expression and sorts the label values of the input expression result. This function uses `natural sort order`.

Expression:
```text
sort_label_values(Expression, <order>, <label1_name>, <label2_name> ...)
```
- `order` is the order of the sort results. The value of `order` can be `asc` or `des`.
- `<label1_name>, <label2_name> ...` is the label names that need to be sorted by their values. At least one label name should be specified.
The labels in the head of the list will be sorted first, and if the label not be included in the expression result will be ignored.

For example:
If we want to sort the `service_percentile` metric label values in descending order by the `p` label, we can use the following expression:
```text
sort_label_values(service_percentile{p='50,75,90,95,99'}, des, p)
```

For multiple labels, assume the metric has 2 labels：
```text
metric{label1='a', label2='2a'} 
metric{label1='a', label2='2c'}
metric{label1='b', label2='2a'}
metric{label1='b', label2='2c'}
```
If we want to sort the `metric` metric label values in descending order by the `label1` and `label2` labels, we can use the following expression:
```text
sort_label_values(metric, des, label1, label2)
```
And the result will be:
```text
metric{label1='b', label2='2c'}
metric{label1='b', label2='2a'}
metric{label1='a', label2='2c'}
metric{label1='a', label2='2a'}
```

## Expression Query Example
### Labeled Value Metrics
```text
service_percentile{p='50,95'}
```
The example result is:
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": [{"key": "p", "value": "50"}]
          },
          "values": [{"id": "1691658000000", "value": "1000", "traceID": null}, {"id": "1691661600000", "value": 2000, "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "p", "value": "75"}]
          },
          "values": [{"id": "1691658000000", "value": "2000", "traceID": null}, {"id": "1691661600000", "value": 3000, "traceID": null}]
        }
      ]
    }
  }
}
```
If we want to transform the percentile value unit from `ms` to `s` the expression is:
```text
service_percentile{p='50,75'} / 1000
```
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": [{"key": "p", "value": "50"}]
          },
          "values": [{"id": "1691658000000", "value": "1", "traceID": null}, {"id": "1691661600000", "value": 2, "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "p", "value": "75"}]
          },
          "values": [{"id": "1691658000000", "value": "2", "traceID": null}, {"id": "1691661600000", "value": 3, "traceID": null}]
        }
      ]
    }
  }
}
```
Get the average value of each percentile, the expression is:
```text
avg(service_percentile{p='50,75'})
```
```json
{
  "data": {
    "execExpression": {
      "type": "SINGLE_VALUE",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": [{"key": "p", "value": "50"}]
          },
          "values": [{"id": null, "value": "1500", "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "p", "value": "75"}]
          },
          "values": [{"id": null, "value": "2500", "traceID": null}]
        }
      ]
    }
  }
}
```
Calculate the difference between the percentile and the average value, the expression is:
```text
service_percentile{p='50,75'} - avg(service_percentile{p='50,75'})
```
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": [{"key": "p", "value": "50"}]
          },
          "values": [{"id": "1691658000000", "value": "-500", "traceID": null}, {"id": "1691661600000", "value": 500, "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "p", "value": "75"}]
          },
          "values": [{"id": "1691658000000", "value": "-500", "traceID": null}, {"id": "1691661600000", "value": 500, "traceID": null}]
        }
      ]
    }
  }
}
```
Calculate the difference between the `service_resp_time` and the `service_percentile`, if the `service_resp_time` result is:
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": []
          },
          "values": [{"id": "1691658000000", "value": "2500", "traceID": null}, {"id": "1691661600000", "value": 3500, "traceID": null}]
        }
      ]
    }
  }
}
```
The expression is:
```text
service_resp_time - service_percentile{p='50,75'}
```
```json
{
  "data": {
    "execExpression": {
      "type": "TIME_SERIES_VALUES",
      "error": null,
      "results": [
        {
          "metric": {
            "labels": [{"key": "p", "value": "50"}]
          },
          "values": [{"id": "1691658000000", "value": "1500", "traceID": null}, {"id": "1691661600000", "value": "1500", "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "p", "value": "75"}]
          },
          "values": [{"id": "1691658000000", "value": "500", "traceID": null}, {"id": "1691661600000", "value": "500", "traceID": null}]
        }
      ]
    }
  }
}
```    
