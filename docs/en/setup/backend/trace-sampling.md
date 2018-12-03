# Trace Sampling at server side
When we run a distributed tracing system, the trace bring us detailed info, but cost a lot at storage.
Open server side trace sampling mechanism, the metric of service, service instance, endpoint and topology are all accurate
as before, but only don't save all the traces into storage.

Of course, even you open sampling, the traces will be kept as consistent as possible. **Consistent** means, once the trace
segments have been collected and reported by agents, the backend would do their best to don't break the trace. See [Recommendation](#recommendation)
to understand why we called it `as consistent as possible` and `do their best to don't break the trace`.

## Set the sample rate
In **receiver-trace** receiver, you will find `sampleRate` setting.

```yaml
receiver-trace:
  default:
    bufferPath: ../trace-buffer/  # Path to trace buffer files, suggest to use absolute path
    bufferOffsetMaxFileSize: 100 # Unit is MB
    bufferDataMaxFileSize: 500 # Unit is MB
    bufferFileCleanWhenRestart: false
    sampleRate: ${SW_TRACE_SAMPLE_RATE:1000} # The sample rate precision is 1/10000. 10000 means 100% sample in default.
```

`sampleRate` is for you to set sample rate to this backend. 
The sample rate precision is 1/10000. 10000 means 100% sample in default. 

# Recommendation
You could set different backend instances with different `sampleRate` values, but we recommend you to set the same.

When you set the rate different, let's say
* Backend-Instance**A**.sampleRate = 35
* Backend-Instance**B**.sampleRate = 55

And we assume the agents reported all trace segments to backend,
Then the 35% traces in the global will be collected and saved in storage consistent/complete, with all spans.
20% trace segments, which reported to Backend-Instance**B**, will saved in storage, maybe miss some trace segments,
because they are reported to Backend-Instance**A** and ignored.