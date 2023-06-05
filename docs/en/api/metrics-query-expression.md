# Metrics Query Expression(MQE) Syntax
MQE is a string that consists of one or more expressions. Each expression could be a combination of one or more operations. 
The expression allows users to do simple query-stage calculation through [MQE APIs](./query-protocol.md#mqe-apis).    

```text
Expression = <Operation> Expression1 <Operation> Expression2 <Operation> Expression3 ...
```

The following document lists the operations supported by MQE.

## Metrics Expression
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

### Labeled Value Metrics
Expression:
```text
<metric_name>{labels='<label_name1>,...', relabels='<new_label_name1>,...'}
```
`labels` is the selected labels name of the metric, and `relabels` is the labels name of the metric after the label is renamed.

If `labels` is not specified, all labels of the metric will be selected. And if `relabels` is not specified, the label name will not be changed.

For example:
If we want to query the `service_percentile` metric with the labels `0,1,2,3,4`, and rename the labels to `P50,P75,P90,P95,P99`, we can use the following expression:
```text
service_percentile{labels='0,1,2,3,4', relabels='P50,P75,P90,P95,P99'}
```


## Binary Operation 
Binary Operation is an operation that takes two expressions and performs a calculation on their results.
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

## Aggregation Operation
Aggregation Operation takes an expression and performs aggregate calculation on its results.


Expression:
```text
<Aggregation-Operator>(Expression)
```


| Operator | Definition                     |
|----------|--------------------------------|
| avg      | average the result             |
| count    | count number of the result     |
| sum      | sum the result                 |
| max      | select maximum from the result |
| min      | select minimum from the result |

For example:
If we want to query the average value of the `service_cpm` metric, we can use the following expression:

```text
avg(service_cpm)
```


## Function Operation
Function Operation takes an expression and performs function calculation on its results.

<Function-Operator>(Expression, parameters)

| Operator | Definition                                                                | parameters                                                         |
|----------|---------------------------------------------------------------------------|--------------------------------------------------------------------|
| abs      | returns the absolute value of the result                                  |                                                                    |
| ceil     | returns the smallest integer value that is greater or equal to the result |                                                                    |
| floor    | returns the largest integer value that is greater or equal to the result  |                                                                    |
| round    | returns result round to specific decimal places                           | `places`: a positive integer specific decimal places of the result |

For example:
If we want to query the average value of the `service_cpm` metric in seconds, 
and round the result to 2 decimal places, we can use the following expression:

```text
round(service_cpm / 60 , 2)
```

## TopN Operation
TopN Operation takes an expression and performs TopN calculation on its results.
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
