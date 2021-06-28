# Custom trace sampling rate at backend side.

When we only want to see the exception trace, or the delay trace, but we do not want to configure the global by [Trace-sampling](trace-sampling.md) and `slowTraceSegmentThreshold` of `application.yml` by reason of 
the storage layer cannot support large data load.

We can configure sampling rate dynamically follow the service or instance or interface latitude.

```
services:
  - name: serverName # the server name which show skywalking-ui
    instances:
      - name: hostName@127.0.0.1 # the server instance which show skywalking-ui
        sampleRate: 900 # instance latitude
        duration: 10000 # trace latency time
    endpoints:
      - name: '/url' # the endpoint
        sampleRate: 800
        duration: 10000 # trace latency time
    sampleRate: 1000 # endpoint latitude
    duration: 10000 # trace latency time
```
**Note**
It has a higher priority than global `agent-analyzer.default.sampleRate` and `agent-analyzer.default.slowTraceSegmentThreshold`.

