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

# Recommendation
You may choose to set different backend instances with different `sampleRate` values, although we recommend that you set the values to be the same.

When you set the different rates, let's say:
* Backend-Instance**A**.sampleRate = 35
* Backend-Instance**B**.sampleRate = 55

Assume the agents have reported all trace segments to the backend. 35% of the traces at the global level will be collected and saved in storage consistently/completely together with all spans. 20% of the trace segments reported to Backend-Instance **B** will be saved in storage, whereas some trace segments may be missed, as they are reported to Backend-Instance**A** and ignored.

# Note
When you enable sampling, the actual sample rate may exceed sampleRate. The reason is that currently, all error/slow segments will be saved; meanwhile, the upstream and downstream may not be sampled. This feature ensures that you have the error/slow stacks and segments, although it is not guaranteed that you would have the whole traces.

Note that if most of the accesses have failed or are slow, the sampling rate would be close to 100%. This may cause the backend or storage clusters to crash.
