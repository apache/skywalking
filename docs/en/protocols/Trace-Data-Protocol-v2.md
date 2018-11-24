# Trace Data Protocol v2
Trace Data Protocol describes the data format between SkyWalking agent/sniffer and backend. 

## Overview
Trace data protocol is defined and provided in [gRPC format](../../../apm-protocol/apm-network/src/main/proto).

For each agent/SDK, it needs to register service id and service instance id before reporting any kind of trace 
or metric data.

### Step 1. Do register
[Register service](../../../apm-protocol/apm-network/src/main/proto/register/Register.proto) takes charge of 
all register methods. At step 1, we need `doServiceRegister`, then `doServiceInstanceRegister`.

1. First of all, do `doServiceRegister`, input is **serviceName**, which could be declared by any UTF-8 String. The return 
value is KeyValue pair, **serviceName** as key, **service id** as value. Batch is also supported.
1. After have **service id**, use `doServiceInstanceRegister` to do instance register. Input is **service id**, **UUID**,
and **register time**. UUID should be unique in the whole distributed environments. The return value is still KeyValue pair,
**UUID** as key, **service instance id** as value. Batch is also supported.

For register, the most important notice is that, the process is expected as async in backend, so, the return could be **NULL**.
In most cases, you need to set a timer to call these services repeated, until you got the response. Suggestion loop cycle, 10s.

Because batch is supported, even for most language agent/SDK, no scenario to do batch register. We suggest to check the  `serviceName`
and `UUID` in response, and match with your expected value.

### Step 2. Send trace and metric
After you have trace id and trace instance id, you could send traces and metric. Now we
have 
1. `TraceSegmentReportService#collect` for skywalking native trace format
1. `JVMMetricReportService#collect` for skywalking native jvm format

For trace format, there are some notices
1. Segment is a concept in SkyWalking, it should include all span for per request in a single OS process, usually single thread based on language.
2. Span has 3 different groups.

* EntrySpan
EntrySpan represents a service provider, also the endpoint of server side. As an APM system, we are targeting the 
application servers. So almost all the services and MQ-comsumer are EntrySpan(s).

* LocalSpan
LocalSpan represents a normal Java method, which don't relate with remote service, neither a MQ producer/comsumer
nor a service(e.g. HTTP service) provider/consumer.

* ExitSpan
ExitSpan represents a client of service or MQ-producer, as named as `LeafSpan` at early age of SkyWalking.
e.g. accessing DB by JDBC, reading Redis/Memcached are cataloged an ExitSpan. 

3. Span parent info called Reference, which is included in span. Reference carries more fields besides 
trace id, parent segment id, span id. Others are **entry service instance id**, **parent service instance id**,
**entry endpoint**, **parent endpoint** and **network address**. Follow [SkyWalking Trace Data Protocol v2](Trace-Data-Protocol-v2.md),
you will know how to get all these fields.

### Step 3. Keep alive.
`ServiceInstancePing#doPing` should be called per several seconds. Make the backend know this instance is still
alive. Existed **service instance id** and **UUID** used in `doServiceInstanceRegister` are required.