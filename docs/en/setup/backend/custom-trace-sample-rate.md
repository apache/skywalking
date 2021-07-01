# Custom trace sampling rate at backend side.

When we only want to see the exception trace, or the delay trace, but we do not want to configure the global by [Trace-sampling](trace-sampling.md) and `slowTraceSegmentThreshold` of `application.yml` by reason of 
the storage layer cannot support large data load.

We can configure sampling rate dynamically for the specified services

```
services:
  - name: serverName # the server name which show skywalking-ui
    sampleRate: 1000 # endpoint latitude
    duration: 10000 # trace latency time
```
**Note**
It has a higher priority than global `agent-analyzer.default.sampleRate` and `agent-analyzer.default.slowTraceSegmentThreshold`.

