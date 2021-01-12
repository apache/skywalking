# Slow Database Statement
Slow Database statements are significant important to find out the bottleneck of the system, which relied on Database.

Slow DB statements are based on sampling, right now, the core samples top 50 slowest in every 10 minutes.
But duration of those statements must be slower than threshold.

The setting format is following, unit is millisecond.
> database-type:thresholdValue,database-type2:thresholdValue2

Default setting is `default:200,mongodb:100`. `Reserved DB type` is **default**, which be as default threshold for all
database types, except set explicitly.

**Notice**, the threshold should not be too small, like `1ms`. Functionally, it works, but would cost OAP performance issue,
if your system statement access time are mostly more than 1ms.