# Trace Data Protocol v3
Trace Data Protocol describes the data format between SkyWalking agent/sniffer and backend. 

## Overview
Trace data protocol is defined and provided in [gRPC format](https://github.com/apache/skywalking-data-collect-protocol),
also implemented in [HTTP 1.1](HTTP-API-Protocol.md)

### Report service instance status
1. Service Instance Properties 
Service instance contains more information than just a name. Once the agent wants to report this, use `ManagementService#reportInstanceProperties` service
to provide a string-key/string-value pair list as the parameter. `language` of target instance is expected at least.

2. Service Ping
Service instance should keep alive with the backend. The agent should set a scheduler using `ManagementService#keepAlive` service every minute.

### Send trace and metrics
After you have the service ID and service instance ID ready, you could send traces and metrics. Now we
have 
1. `TraceSegmentReportService#collect` for the SkyWalking native trace format
1. `JVMMetricReportService#collect` for the SkyWalking native jvm format

For trace format, note that:
1. The segment is a unique concept in SkyWalking. It should include all spans for each request in a single OS process, which is usually a single language-based thread.
2. There are three types of spans.

* EntrySpan
EntrySpan represents a service provider, which is also the endpoint on the server end. As an APM system, SkyWalking targets the 
application servers. Therefore, almost all the services and MQ-consumers are EntrySpans.

* LocalSpan
LocalSpan represents a typical Java method which is not related to remote services. It is neither a MQ producer/consumer
nor a provider/consumer of a service (e.g. HTTP service).

* ExitSpan
ExitSpan represents a client of service or MQ-producer. It is known as the `LeafSpan` in the early stages of SkyWalking.
For example, accessing DB by JDBC, and reading Redis/Memcached are classified as ExitSpans. 

3. Cross-thread/process span parent information is called "reference". Reference carries the trace ID, 
segment ID, span ID, service name, service instance name, endpoint name, and target address used on the client end (note: this is not required in cross-thread operations) 
of this request in the parent. 
See [Cross Process Propagation Headers Protocol v3](Skywalking-Cross-Process-Propagation-Headers-Protocol-v3.md) for more details.

4. `Span#skipAnalysis` may be TRUE, if this span doesn't require backend analysis.

