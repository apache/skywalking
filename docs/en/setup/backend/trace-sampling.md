# Trace Sampling at server side
An advantage of a distributed tracing system is that detailed information from the traces can be obtained. However, the downside is that these traces use up a lot of storage.

If you enable the trace sampling mechanism at the **server-side**, you will find that the service metrics, service instance, endpoint, and topology all have the same accuracy as before. The only difference is that they do not save all traces in storage.

Of course, even if you enable sampling, the traces will be kept as consistent as possible. Being **consistent** means that once the trace
segments have been collected and reported by agents, the backend would make its best effort not to split the traces. See our [recommendation](#recommendation)
to understand why you should keep the traces as consistent as possible and try not to split them.

## Set the sample rate
In the **agent-analyzer** module, you will find the `sampleRate` setting by the configuration `traceSamplingPolicySettingsFile`.

```yaml
agent-analyzer:
  default:
    ...
    # The default sampling rate and the default trace latency time configured by the 'traceSamplingPolicySettingsFile' file.
    traceSamplingPolicySettingsFile: ${SW_TRACE_SAMPLING_POLICY_SETTINGS_FILE:trace-sampling-policy-settings.yml}
    forceSampleErrorSegment: ${SW_FORCE_SAMPLE_ERROR_SEGMENT:true} # When sampling mechanism activated, this config would make the error status segment sampled, ignoring the sampling rate.
```

The default `trace-sampling-policy-settings.yml` uses the following format. Could use [dynamic configuration](dynamic-config.md) to update the settings in the runtime.
```yaml
default:
  # Default sampling rate that replaces the 'agent-analyzer.default.sampleRate'
  # The sample rate precision is 1/10000. 10000 means 100% sample in default.
  rate: 10000
  # Default trace latency time that replaces the 'agent-analyzer.default.slowTraceSegmentThreshold'
  # Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism is activated. The default value is `-1`, which would not sample slow traces. Unit, millisecond.
  duration: -1
#services:
#  - name: serverName
#    rate: 1000 # Sampling rate of this specific service
#    duration: 10000 # Trace latency threshold for trace sampling for this specific service
```

`duration.rate` allows you to set the sample rate to this backend.
The sample rate precision is 1/10000. 10000 means 100% sample by default.

`forceSampleErrorSegment` allows you to save all error segments when the sampling mechanism is activated.
This config will cause the error status segment to be sampled when the sampling mechanism is activated, ignoring the sampling rate.

`default.duration` allows you to save all slow trace segments when the sampling mechanism is activated.
Setting this threshold on latency (in milliseconds) would cause slow trace segments to be sampled if they use up more time, even if the sampling mechanism is activated. The default value is `-1`, which means that slow traces would not be sampled.

**Note:**
`services.[].rate` and `services.[].duration` has a higher priority than `default.rare` and `default.duration`.

# Other trace sampling mechanisms
The `agent-analyzer` sampling above applies to SkyWalking-native trace segments. Two other
mechanisms can drop traces independently of it.

## Zipkin receiver sampling
Zipkin spans do not pass through `agent-analyzer`, so they are sampled by the receiver instead,
also at ingest and before storage.

```yaml
receiver-zipkin:
  default:
    # The sample rate precision is 1/10000, should be between 0 and 10000
    sampleRate: ${SW_ZIPKIN_SAMPLE_RATE:10000}
    # The maximum spans to be collected per second. 0 means no limit. Spans exceeding this threshold will be dropped.
    maxSpansPerSecond: ${SW_ZIPKIN_MAX_SPANS_PER_SECOND:0}
```

`sampleRate` keeps a span when `abs(traceId) <= Long.MAX_VALUE * sampleRate / 10000`. Because the
boundary is derived from the trace ID, every span of a given trace is kept or dropped together.
A span with `debug=true` is always kept, ignoring the sample rate.

`maxSpansPerSecond` is a rate limiter applied **per span**, before the rate check. Unlike the
other mechanisms it is not trace-consistent: when the limit is hit it can drop some spans of a
trace while keeping others, leaving partial traces in storage.

## BanyanDB post-trace retention (trace pipeline)
When the storage is BanyanDB, a group may additionally run a sampler plugin **inside BanyanDB**,
at LSM merge time — after the data has been written. It reclaims space from stored traces rather
than preventing writes, and unlike the ingest-side mechanisms it decides per whole trace, seeing
all of a trace's segments at once. It is disabled by default; see
[BanyanDB storage](storages/banyandb.md) for the `pipeline` settings.

## How they combine
These mechanisms are independent gates, so enabling more than one **multiplies** the drop rate.
For example, an `agent-analyzer` rate of `5000` (50%) together with a BanyanDB
`healthySampleRate` of `0.1` retains roughly 5% of healthy traces. Prefer sampling at one
layer: at ingest it is cheaper (the data is never stored), while at merge the verdict has the
whole-trace context and can also reclaim space that is already written.

All of these default to "keep everything" (`rate: 10000`, `sampleRate: 10000`,
`maxSpansPerSecond: 0`, and the BanyanDB pipeline disabled), so none of them sample until you
turn one on.

# Recommendation
You may choose to set different backend instances with different `sampleRate` values, although we recommend that you set the values to be the same.

When you set the different rates, let's say:
* Backend-Instance**A**.sampleRate = 35
* Backend-Instance**B**.sampleRate = 55

Assume the agents have reported all trace segments to the backend. 35% of the traces at the global level will be collected and saved in storage consistently/completely together with all spans. 20% of the trace segments reported to Backend-Instance **B** will be saved in storage, whereas some trace segments may be missed, as they are reported to Backend-Instance**A** and ignored.

# Note
When you enable sampling, the actual sample rate may exceed sampleRate. The reason is that currently, all error/slow segments will be saved; meanwhile, the upstream and downstream may not be sampled. This feature ensures that you have the error/slow stacks and segments, although it is not guaranteed that you would have the whole traces.

Note that if most of the accesses have failed or are slow, the sampling rate would be close to 100%. This may cause the backend or storage clusters to crash.
