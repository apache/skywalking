# Trace Data Protocol v3
Trace Data Protocol describes the data format between SkyWalking agent/sniffer and backend. 

## Overview
Trace data protocol is defined and provided in [gRPC format](https://github.com/apache/skywalking-data-collect-protocol),
also implemented in [HTTP 1.1](HTTP-API-Protocol.md)

### Report service instance status
1. Service Instance Properties 
Service instance has more information than a name, once the agent wants to report this, use `ManagementService#reportInstanceProperties` service
providing a string-key/string-value pair list as the parameter. `language` of target instance is expected at least.

2. Service Ping
Service instance should keep alive with the backend. The agent should set a scheduler using `ManagementService#keepAlive` service in every minute.

### Send trace and metrics
After you have service id and service instance id, you could send traces and metrics. Now we
have 
1. `TraceSegmentReportService#collect` for skywalking native trace format
1. `JVMMetricReportService#collect` for skywalking native jvm format

For trace format, there are some notices
1. Segment is a concept in SkyWalking, it should include all span for per request in a single OS process, usually single thread based on language.
2. Span has 3 different groups.

* EntrySpan
EntrySpan represents a service provider, also the endpoint of server side. As an APM system, we are targeting the 
application servers. So almost all the services and MQ-consumer are EntrySpan(s).

* LocalSpan
LocalSpan represents a normal Java method, which don't relate with remote service, neither a MQ producer/consumer
nor a service(e.g. HTTP service) provider/consumer.

* ExitSpan
ExitSpan represents a client of service or MQ-producer, as named as `LeafSpan` at early age of SkyWalking.
e.g. accessing DB by JDBC, reading Redis/Memcached are cataloged an ExitSpan. 

3. Span across thread or process parent info is called Reference. Reference carries trace id, 
segment id, span id, service name, service instance name, endpoint name and target address used at client side(not required in across thread) 
of this request in the parent. 
Follow [Cross Process Propagation Headers Protocol v3](Skywalking-Cross-Process-Propagation-Headers-Protocol-v3.md) to get more details.

4. `Span#skipAnalysis` could be TRUE, if this span doesn't require backend analysis.

