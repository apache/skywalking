# Meter System
Meter system is another streaming calculation mode, especially for metrics data. In the [OAL](oal.md), there are clear 
[Scope Definitions](scope-definitions.md), including native objects. Meter system is focusing on the data type itself,
and provides more flexible to the end user to define the scope entity.

The meter system is open to different receivers and fetchers in the backend, 
follow the [backend setup document](../setup/backend/backend-setup.md) for more details.

Every metrics is declared in the meter system should include following attribute
1. **Metrics Name**. An unique name globally, should avoid overlap the OAL variable names.
1. **Function Name**. The function used for this metrics, distributed aggregation, value calculation and down sampling calculation
based on the function implementation. Also, the data structure is determined by the function too, such as function Avg is for Long.
1. **Scope Type**. Unlike inside the OAL, there are plenty of logic scope definitions, in meter system, only type is required. 
Type values include service, instance, and endpoint, like we introduced in the Overview.
The values of scope entity name, such as service name, are required when metrics data generated with the metrics data value.

NOTICE, the metrics must be declared in the bootstrap stage, no runtime changed.

Meter System supports following binding functions
- **avg**. Calculate the avg value for every entity in the same metrics name.
- **histogram**. Aggregate the counts in the configurable buckets, buckets is configurable but must be assigned in the declaration stage.
- **percentile**. Read [percentile in WIKI](https://en.wikipedia.org/wiki/Percentile). Unlike in the OAL, we provide
50/75/90/95/99 in default, in the meter system function, percentile function accepts several ranks, which should be in
the (0, 100) range.
