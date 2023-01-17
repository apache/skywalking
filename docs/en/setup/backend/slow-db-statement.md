# Slow Database Statement
Slow Database statements are crucial for you to identify bottlenecks of a system which relies on databases.

Slow DB statements are based on sampling. Right now, the core samples are the top 50 slowest every 10 minutes.
Note that the duration of these statements must be slower than the threshold.

Here's the format of the settings (in milliseconds):
> database-type:thresholdValue,database-type2:thresholdValue2

The default settings are `default:200,mongodb:100`. `Reserved DB type` is **default**, which is the default threshold for all
database types, unless set explicitly.

**Note**: 
1. The threshold should not be set too small, like `1ms`. Although it works in theory, OAP performance issues may arise if your system statement access time is usually more than 1ms.
2. The OAP server would run statistic per service and only persistent the top 50 every 10(controlled by `topNReportPeriod: ${SW_CORE_TOPN_REPORT_PERIOD:10}`) minutes by default.

