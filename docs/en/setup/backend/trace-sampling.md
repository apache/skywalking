# Trace Sampling at server side
When we run a distributed tracing system, the trace bring us detailed info, but cost a lot at storage.
Open server side trace sampling mechanism, the metrics of service, service instance, endpoint and topology are all accurate
as before, but only don't save all the traces into storage.

Of course, even you open sampling, the traces will be kept as consistent as possible. **Consistent** means, once the trace
segments have been collected and reported by agents, the backend would do their best to don't break the trace. See [Recommendation](#recommendation)
to understand why we called it `as consistent as possible` and `do their best to don't break the trace`.

## Set the sample rate
In **agent-analyzer** module, you will find `sampleRate` setting.

```yaml
agent-analyzer:
  default:
    ...
    sampleRate: ${SW_TRACE_SAMPLE_RATE:10000} # The sample rate precision is 1/10000. 10000 means 100% sample in default.
    forceSampleErrorSegment: ${SW_FORCE_SAMPLE_ERROR_SEGMENT:true} # When sampling mechanism activated, this config would make the error status segment sampled, ignoring the sampling rate.
    slowTraceSegmentThreshold: ${SW_SLOW_TRACE_SEGMENT_THRESHOLD:-1} # Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond.
```

`sampleRate` is for you to set sample rate to this backend.
The sample rate precision is 1/10000. 10000 means 100% sample in default.

`forceSampleErrorSegment` is for you to save all error segments when sampling mechanism actived.
When sampling mechanism activated, this config would make the error status segment sampled, ignoring the sampling rate.

`slowTraceSegmentThreshold` is for you to save all slow trace segments when sampling mechanism actived.
Setting this threshold about the latency would make the slow trace segments sampled if they cost more time, even the sampling mechanism activated. The default value is `-1`, which means would not sample slow traces. Unit, millisecond.

# Recommendation
You could set different backend instances with different `sampleRate` values, but we recommend you to set the same.

When you set the rate different, let's say
* Backend-Instance**A**.sampleRate = 35
* Backend-Instance**B**.sampleRate = 55

And we assume the agents reported all trace segments to backend,
Then the 35% traces in the global will be collected and saved in storage consistent/complete, with all spans.
20% trace segments, which reported to Backend-Instance**B**, will saved in storage, maybe miss some trace segments,
because they are reported to Backend-Instance**A** and ignored.

# Note
When you open sampling, the actual sample rate could be over sampleRate. Because currently, all error/slow segments will be saved, meanwhile, the upstream and downstream may not be sampled. This feature is going to make sure you could have the error/slow stacks and segments, but don't guarantee you would have the whole trace.

Also, the side effect would be, if most of the accesses are fail/slow, the sampling rate would be closing to 100%, which could crash the backend or storage clusters.
