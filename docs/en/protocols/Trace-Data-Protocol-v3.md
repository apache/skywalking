# Trace Data Protocol v2
Trace Data Protocol describes the data format between SkyWalking agent/sniffer and backend. 

## Overview
Trace data protocol is defined and provided in [gRPC format](https://github.com/apache/skywalking-data-collect-protocol).

For each agent/SDK, it needs to register service id and service instance id before reporting any kind of trace 
or metrics data.

Since SkyWalking v8.x, SkyWalking provided register and uplink trace data through HTTP API way.
[HTTP API Protocol](HTTP-API-Protocol.md) defined the API data format.

### Report service instance properties 
Service Instance has more information than a name, once the agent wants to report this, use `ServiceInstanceService#reportProperties` service
providing a string-key/string-value pair list as the parameter. `language` of target instance is expected at least.

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

3. Span parent info called Reference, which is included in span. Reference carries more fields besides 
trace id, parent segment id, span id. Others are **entry service instance id**, **parent service instance id**,
**entry endpoint**, **parent endpoint** and **network address**. Follow [Cross Process Propagation Headers Protocol v2](Skywalking-Cross-Process-Propagation-Headers-Protocol-v3.md),
you will know how to get all these fields.

4. `segment` in Upstream is the byte array of TraceSegmentObject.

### Step 3. Keep alive.
`ServiceInstancePing#doPing` should be called per several seconds. Make the backend know this instance is still
alive. Existed **service instance id** and **UUID** used in `doServiceInstanceRegister` are required.
