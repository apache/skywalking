# Query in OAP
Query is the core feature of OAP for visualization and other higher system. The query matches the metric type.

There are two types of query provided.
1. Hard codes query implementor
1. Metric style query of implementor

## Hard codes
Hard codes query implementor, is for complex logic query, such as: topology map, dependency map, which 
most likely relate to mapping mechanism of the node relationship.

Even so, hard codes implementors are based on metric style query too, just need extra codes to assemble the 
results.

## Metric style query
Metric style query is based on the given scope and metric name in oal scripts.

Metric style query provided in two ways
- GraphQL way. UI uses this directly, and assembles the pages.
- API way. Most for `Hard codes query implementor` to do extra works.

### Grammar
```
Metric.Scope(SCOPE).Func(METRIC_NAME [, PARAM ...])
```

### Scope
**SCOPE** in (`All`, `Service`, `ServiceInst`, `Endpoint`, `ServiceRelation`, `ServiceInstRelation`, `EndpointRelation`).

### Metric name
Metric name is defined in oal script. Such as **EndpointCalls** is the name defined by `EndpointCalls = from(Endpoint.*).sum()`.

### Metric Query Function
Metric Query Functions match the Aggregation Function in most cases, but include some order or filter features.
Try to keep the name as same as the aggregation functions.

Provided functions
- `top`
- `trend`
- `histogram`
- `sum`

### Example
For `avg` aggregate func, `top` match it, also with parameter[1] of result size and parameter[2] of order
```
# for Service_avg = from(Service.latency).avg()
Metric.Scope("Service").topn("Service_avg", 10, "desc")
```