# Slow Cache Command
Slow Cache command are sensitive for you to identify bottlenecks of a system which relies on cache system.

Slow Cache command are based on sampling. Right now, the core samples are the top 50 slowest every 10 minutes.
Note that the duration of these command must be slower than the threshold.

Here's the format of the settings (in milliseconds):
> cache-type:thresholdValue,cache-type2:thresholdValue2

The default settings are `default:20,redis:10`. `Reserved Cache type` is **default**, which is the default threshold for all
cache types, unless set explicitly.

**Note**: The threshold should not be set too small, like `1ms`. Although it works in theory, OAP performance issues may arise if your system statement access time is usually more than 1ms.
