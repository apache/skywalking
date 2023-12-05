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
For now, we only have a single anonymous label with multi label values in a labeled metric. 
To be able to use it in expressions, define `_` as the anonymous label name (key).

Expression:
```text
<metric_name>{_='<label_value_1>,...'}
```
`{_='<label_value_1>,...'}` is the selected label value of the metric. If is not specified, all label values of the metric will be selected. 

For example:
If we want to query the `service_percentile` metric with the label values `0,1,2,3,4`, we can use the following expression:
```text
service_percentile{_='0,1,2,3,4'}
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
**Note**: If the expressions on both sides of the operator are the `TIME_SERIES_VALUES with labels`, they should have the same labels for calculation.

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
TopN Operation takes an expression and performs TopN calculation on its results.

Expression:
```text
top_n(<metric_name>, <top_number>, <order>)
```

`top_number` is the number of the top results, should be a positive integer.

`order` is the order of the top results. The value of `order` can be `asc` or `des`.

For example:
If we want to query the top 10 services with the highest `service_cpm` metric value, we can use the following expression:

```text
top_n(service_instance_cpm, 10, des)
```

### Result Type
According to the type of the metric, the `ExpressionResultType` of the expression will be `SORTED_LIST` or `RECORD_LIST`.

## Relabel Operation
Relabel Operation takes an expression and replaces the label values with new label values on its results.

Expression:
```text
relabel(Expression, _='<new_label_value_1>,...')
```

`_` is the new label of the metric after the label is relabeled, the order of the new label values should be the same as the order of the label values in the input expression result.

For example:
If we want to query the `service_percentile` metric with the label values `0,1,2,3,4`, and rename the label values to `P50,P75,P90,P95,P99`, we can use the following expression:

```text
relabel(service_percentile{_='0,1,2,3,4'}, _='P50,P75,P90,P95,P99')
```

### Result Type
Follow the input expression.

## AggregateLabels Operation
AggregateLabels Operation takes an expression and performs an aggregate calculation on its `Labeled Value Metrics` results. It aggregates a group of `TIME_SERIES_VALUES` into a single `TIME_SERIES_VALUES`.

Expression:
```text
aggregate_labels(Expression, parameter)
```

| parameter | Definition                                          | ExpressionResultType |
|-----------|-----------------------------------------------------|----------------------|
| avg       | calculate avg value of a `Labeled Value Metrics`    | TIME_SERIES_VALUES   |
| sum       | calculate sum value of a `Labeled Value Metrics`    | TIME_SERIES_VALUES   |
| max       | select the maximum value from a `Labeled Value Metrics` | TIME_SERIES_VALUES   |
| min       | select the minimum value from a `Labeled Value Metrics` | TIME_SERIES_VALUES   |

For example:
If we want to query all Redis command total rates, we can use the following expression(`total_commands_rate` is a metric which recorded every command rate in labeled value):

```text
aggregate_labels(total_commands_rate, SUM)
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

## Expression Query Example
### Labeled Value Metrics
```text
service_percentile{_='0,1'}
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
            "labels": [{"key": "_", "value": "0"}]
          },
          "values": [{"id": "1691658000000", "value": "1000", "traceID": null}, {"id": "1691661600000", "value": 2000, "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "_", "value": "1"}]
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
service_percentile{_='0,1'} / 1000
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
            "labels": [{"key": "_", "value": "0"}]
          },
          "values": [{"id": "1691658000000", "value": "1", "traceID": null}, {"id": "1691661600000", "value": 2, "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "_", "value": "1"}]
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
avg(service_percentile{_='0,1'})
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
            "labels": [{"key": "_", "value": "0"}]
          },
          "values": [{"id": null, "value": "1500", "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "_", "value": "1"}]
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
service_percentile{_='0,1'} - avg(service_percentile{_='0,1'})
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
            "labels": [{"key": "_", "value": "0"}]
          },
          "values": [{"id": "1691658000000", "value": "-500", "traceID": null}, {"id": "1691661600000", "value": 500, "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "_", "value": "1"}]
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
service_resp_time - service_percentile{_='0,1'}
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
            "labels": [{"key": "_", "value": "0"}]
          },
          "values": [{"id": "1691658000000", "value": "1500", "traceID": null}, {"id": "1691661600000", "value": "1500", "traceID": null}]
        },
        {
          "metric": {
            "labels": [{"key": "_", "value": "1"}]
          },
          "values": [{"id": "1691658000000", "value": "500", "traceID": null}, {"id": "1691661600000", "value": "500", "traceID": null}]
        }
      ]
    }
  }
}
```    
