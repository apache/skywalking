# Zipkin receiver
[Zipkin](http://zipkin.io/) receiver provides the feature to receive span data in Zipkin formats. SkyWalking backend provides
analysis, aggregation and visualization. So the user will not need to learn how SkyWalking auto instrumentation
agents(Java, .NET, node.js) work, or they don't want to change for some reasons, such as Zipkin integration has been completed.

Zipkin receiver is only an optional features in SkyWalking, even now it is [an incubating feature](../../../../docs/en/Incubating/Abstract.md).

## Limits
As an incubating feature, it is a prototype. So it has following limits:

1. Don't try to use SkyWalking native agents and Zipkin's libs in the same distributed system. Considering HEADERs of Zipkin and SkyWalking aren't shared/interoperable, their two will not propagate context for each other. Trace will not continue.
1. Don't support cluster mode.
1. Analysis based on trace will be finished in the certain and given duration. The default assumption is 2 min most. SkyWalking used more complex header and context to avoid this in analysis stage.

## Open Zipkin receiver
Zipkin receiver is an optional module, and default closed. For open it, add these settings in your `application.yml` in collector
```yaml
receiver_zipkin:
  default:
    host: localhost
    port: 9411
    contextPath: /
    expireTime: 20  # Unit is seconds
    maxCacheSize: 1000000  # The number of traces in buffer
```
