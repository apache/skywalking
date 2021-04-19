# Meter System
Meter system is another streaming calculation mode designed for metrics data. In the [OAL](oal.md), there are clear 
[Scope Definitions](scope-definitions.md), including definitions for native objects. Meter system is focused on the data type itself,
and provides a more flexible approach to the end user in defining the scope entity.

The meter system is open to different receivers and fetchers in the backend, 
see the [backend setup document](../setup/backend/backend-setup.md) for more details.

Every metric is declared in the meter system to include the following attributes:
1. **Metrics Name**. A globally unique name to avoid overlapping between the OAL variable names.
1. **Function Name**. The function used for this metric, namely distributed aggregation, value calculation or down sampling calculation
based on the function implementation. Further, the data structure is determined by the function as well, such as function Avg is for Long.
1. **Scope Type**. Unlike within the OAL, there are plenty of logic scope definitions. In the meter system, only type is required. 
Type values include service, instance, and endpoint, just as we have described in the Overview section.
The values of scope entity name, such as service name, are required when metrics data are generated with the metrics data values.

NOTE: The metrics must be declared in the bootstrap stage, and there must be no change to runtime.

The Meter System supports the following binding functions:
- **avg**. Calculates the avg value for every entity under the same metrics name.
- **histogram**. Aggregates the counts in the configurable buckets. Buckets are configurable but must be assigned in the declaration stage.
- **percentile**. See [percentile in WIKI](https://en.wikipedia.org/wiki/Percentile). Unlike the OAL, we provide
50/75/90/95/99 by default. In the meter system function, the percentile function accepts several ranks, which should be in
the (0, 100) range.
