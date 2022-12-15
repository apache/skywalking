# Trace Data Protocol
* Version,  v3.1

Trace Data Protocol describes the data format between SkyWalking agent/sniffer and backend. 

Trace data protocol is defined and provided in [gRPC format](https://github.com/apache/skywalking-data-collect-protocol), and implemented in HTTP 1.1.

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
See [Cross Process Propagation Headers Protocol v3](x-process-propagation-headers-v3.md) for more details.

4. `Span#skipAnalysis` may be TRUE, if this span doesn't require backend analysis.

## Trace Report Protocol
```protobuf
// The segment is a collection of spans. It includes all collected spans in a simple one request context, such as a HTTP request process.
//
// We recommend the agent/SDK report all tracked data of one request once for all, such as,
// typically, such as in Java, one segment represent all tracked operations(spans) of one request context in the same thread.
// At the same time, in some language there is not a clear concept like golang, it could represent all tracked operations of one request context.
message SegmentObject {
    // A string id represents the whole trace.
    string traceId = 1;
    // A unique id represents this segment. Other segments could use this id to reference as a child segment.
    string traceSegmentId = 2;
    // Span collections included in this segment.
    repeated SpanObject spans = 3;
    // **Service**. Represents a set/group of workloads which provide the same behaviours for incoming requests.
    //
    // The logic name represents the service. This would show as a separate node in the topology.
    // The metrics analyzed from the spans, would be aggregated for this entity as the service level.
    string service = 4;
    // **Service Instance**. Each individual workload in the Service group is known as an instance. Like `pods` in Kubernetes, it
    // doesn't need to be a single OS process, however, if you are using instrument agents, an instance is actually a real OS process.
    //
    // The logic name represents the service instance. This would show as a separate node in the instance relationship.
    // The metrics analyzed from the spans, would be aggregated for this entity as the service instance level.
    string serviceInstance = 5;
    // Whether the segment includes all tracked spans.
    // In the production environment tracked, some tasks could include too many spans for one request context, such as a batch update for a cache, or an async job.
    // The agent/SDK could optimize or ignore some tracked spans for better performance.
    // In this case, the value should be flagged as TRUE.
    bool isSizeLimited = 6;
}

// Segment reference represents the link between two existing segment.
message SegmentReference {
    // Represent the reference type. It could be across thread or across process.
    // Across process means there is a downstream RPC call for this.
    // Typically, refType == CrossProcess means SpanObject#spanType = entry.
    RefType refType = 1;
    // A string id represents the whole trace.
    string traceId = 2;
    // Another segment id as the parent.
    string parentTraceSegmentId = 3;
    // The span id in the parent trace segment.
    int32 parentSpanId = 4;
    // The service logic name of the parent segment.
    // If refType == CrossThread, this name is as same as the trace segment.
    string parentService = 5;
    // The service logic name instance of the parent segment.
    // If refType == CrossThread, this name is as same as the trace segment.
    string parentServiceInstance = 6;
    // The endpoint name of the parent segment.
    // **Endpoint**. A path in a service for incoming requests, such as an HTTP URI path or a gRPC service class + method signature.
    // In a trace segment, the endpoint name is the name of first entry span.
    string parentEndpoint = 7;
    // The network address, including ip/hostname and port, which is used in the client side.
    // Such as Client --> use 127.0.11.8:913 -> Server
    // then, in the reference of entry span reported by Server, the value of this field is 127.0.11.8:913.
    // This plays the important role in the SkyWalking STAM(Streaming Topology Analysis Method)
    // For more details, read https://wu-sheng.github.io/STAM/
    string networkAddressUsedAtPeer = 8;
}

// Span represents a execution unit in the system, with duration and many other attributes.
// Span could be a method, a RPC, MQ message produce or consume.
// In the practice, the span should be added when it is really necessary, to avoid payload overhead.
// We recommend to creating spans in across process(client/server of RPC/MQ) and across thread cases only.
message SpanObject {
    // The number id of the span. Should be unique in the whole segment.
    // Starting at 0.
    int32 spanId = 1;
    // The number id of the parent span in the whole segment.
    // -1 represents no parent span.
    // Also, be known as the root/first span of the segment.
    int32 parentSpanId = 2;
    // Start timestamp in milliseconds of this span,
    // measured between the current time and midnight, January 1, 1970 UTC.
    int64 startTime = 3;
    // End timestamp in milliseconds of this span,
    // measured between the current time and midnight, January 1, 1970 UTC.
    int64 endTime = 4;
    // <Optional>
    // In the across thread and across process, these references targeting the parent segments.
    // The references usually have only one element, but in batch consumer case, such as in MQ or async batch process, it could be multiple.
    repeated SegmentReference refs = 5;
    // A logic name represents this span.
    //
    // We don't recommend to include the parameter, such as HTTP request parameters, as a part of the operation, especially this is the name of the entry span.
    // All statistic for the endpoints are aggregated base on this name. Those parameters should be added in the tags if necessary.
    // If in some cases, it have to be a part of the operation name,
    // users should use the Group Parameterized Endpoints capability at the backend to get the meaningful metrics.
    // Read https://github.com/apache/skywalking/blob/master/docs/en/setup/backend/endpoint-grouping-rules.md
    string operationName = 6;
    // Remote address of the peer in RPC/MQ case.
    // This is required when spanType = Exit, as it is a part of the SkyWalking STAM(Streaming Topology Analysis Method).
    // For more details, read https://wu-sheng.github.io/STAM/
    string peer = 7;
    // Span type represents the role in the RPC context.
    SpanType spanType = 8;
    // Span layer represent the component tech stack, related to the network tech.
    SpanLayer spanLayer = 9;
    // Component id is a predefined number id in the SkyWalking.
    // It represents the framework, tech stack used by this tracked span, such as Spring.
    // All IDs are defined in the https://github.com/apache/skywalking/blob/master/oap-server/server-bootstrap/src/main/resources/component-libraries.yml
    // Send a pull request if you want to add languages, components or mapping definitions,
    // all public components could be accepted.
    // Follow this doc for more details, https://github.com/apache/skywalking/blob/master/docs/en/guides/Component-library-settings.md
    int32 componentId = 10;
    // The status of the span. False means the tracked execution ends in the unexpected status.
    // This affects the successful rate statistic in the backend.
    // Exception or error code happened in the tracked process doesn't mean isError == true, the implementations of agent plugin and tracing SDK make the final decision.
    bool isError = 11;
    // String key, String value pair.
    // Tags provides more information, includes parameters.
    //
    // In the OAP backend analysis, some special tag or tag combination could provide other advanced features.
    // https://github.com/apache/skywalking/blob/master/docs/en/guides/Java-Plugin-Development-Guide.md#special-span-tags
    repeated KeyStringValuePair tags = 12;
    // String key, String value pair with an accurate timestamp.
    // Logging some events happening in the context of the span duration.
    repeated Log logs = 13;
    // Force the backend don't do analysis, if the value is TRUE.
    // The backend has its own configurations to follow or override this.
    //
    // Use this mostly because the agent/SDK could know more context of the service role.
    bool skipAnalysis = 14;
}

message Log {
    // The timestamp in milliseconds of this event.,
    // measured between the current time and midnight, January 1, 1970 UTC.
    int64 time = 1;
    // String key, String value pair.
    repeated KeyStringValuePair data = 2;
}

// Map to the type of span
enum SpanType {
    // Server side of RPC. Consumer side of MQ.
    Entry = 0;
    // Client side of RPC. Producer side of MQ.
    Exit = 1;
    // A common local code execution.
    Local = 2;
}

// A ID could be represented by multiple string sections.
message ID {
    repeated string id = 1;
}

// Type of the reference
enum RefType {
    // Map to the reference targeting the segment in another OS process.
    CrossProcess = 0;
    // Map to the reference targeting the segment in the same process of the current one, just across thread.
    // This is only used when the coding language has the thread concept.
    CrossThread = 1;
}

// Map to the layer of span
enum SpanLayer {
    // Unknown layer. Could be anything.
    Unknown = 0;
    // A database layer, used in tracing the database client component.
    Database = 1;
    // A RPC layer, used in both client and server sides of RPC component.
    RPCFramework = 2;
    // HTTP is a more specific RPCFramework.
    Http = 3;
    // A MQ layer, used in both producer and consumer sides of the MQ component.
    MQ = 4;
    // A cache layer, used in tracing the cache client component.
    Cache = 5;
}

// The segment collections for trace report in batch and sync mode.
message SegmentCollection {
    repeated SegmentObject segments = 1;
}
```

## Report Span Attached Events
Besides in-process agents, there are other out-of-process agent, such as ebpf agent, could report additional information
as attached events for the relative spans.

`SpanAttachedEventReportService#collect` for attached event reporting.

```protobuf
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// ebpf agent(SkyWalking Rover) collects extra information from the OS(Linux Only) level to attach on the traced span.
// Since v3.1
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
service SpanAttachedEventReportService {
    // Collect SpanAttachedEvent to the OAP server in the streaming mode.
    rpc collect (stream SpanAttachedEvent) returns (Commands) {
    }
}

// SpanAttachedEvent represents an attached event for a traced RPC.
//
// When an RPC is being traced by the in-process language agent, a span would be reported by the client-side agent.
// And the rover would be aware of this RPC due to the existing tracing header.
// Then, the rover agent collects extra information from the OS level to provide assistance information to diagnose network performance.
message SpanAttachedEvent {
    // The nanosecond timestamp of the event's start time.
    // Notice, most unit of timestamp in SkyWalking is milliseconds, but NANO-SECOND is required here.
    // Because the attached event happens in the OS syscall level, most of them are executed rapidly.
    Instant startTime = 1;
    // The official event name.
    // For example, the event name is a method signature from syscall stack.
    string event = 2;
    // [Optional] The nanosecond timestamp of the event's end time.
    Instant endTime = 3;
    // The tags for this event includes some extra OS level information,
    // such as
    // 1. net_device used for this exit span.
    // 2. network L7 protocol
    repeated KeyStringValuePair tags = 4;
    // The summary of statistics during this event.
    // Each statistic provides a name(metric name) to represent the name, and an int64/long as the value.
    repeated KeyIntValuePair summary = 5;
    // Refer to a trace context decoded from `sw8` header through network, such as HTTP header, MQ metadata
    // https://skywalking.apache.org/docs/main/next/en/protocols/skywalking-cross-process-propagation-headers-protocol-v3/#standard-header-item
    SpanReference traceContext = 6;

    message SpanReference {
        SpanReferenceType type = 1;
        // [Optional] A string id represents the whole trace.
        string traceId = 2;
        // A unique id represents this segment. Other segments could use this id to reference as a child segment.
        // [Optional] when this span reference
        string traceSegmentId = 3;
        // If type == SKYWALKING
        // The number id of the span. Should be unique in the whole segment.
        // Starting at 0
        //
        // If type == ZIPKIN
        // The type of span ID is string.
        string spanId = 4;
    }

    enum SpanReferenceType {
        SKYWALKING = 0;
        ZIPKIN = 1;
    }
}
```

## Via HTTP Endpoint

Detailed information about data format can be found in [Instance Management](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Tracing.proto).
There are two ways to report segment data: one segment per request or segment array in bulk mode.

### POST http://localhost:12800/v3/segment

Send a single segment object in JSON format.

Input:

```json
{
	"traceId": "a12ff60b-5807-463b-a1f8-fb1c8608219e",
	"serviceInstance": "User_Service_Instance_Name",
	"spans": [{
		"operationName": "/ingress",
		"startTime": 1588664577013,
		"endTime": 1588664577028,
		"spanType": "Exit",
		"spanId": 1,
		"isError": false,
		"parentSpanId": 0,
		"componentId": 6000,
		"peer": "upstream service",
		"spanLayer": "Http"
	}, {
		"operationName": "/ingress",
		"startTime": 1588664577013,
		"tags": [{
			"key": "http.method",
			"value": "GET"
		}, {
			"key": "http.params",
			"value": "http://localhost/ingress"
		}],
		"endTime": 1588664577028,
		"spanType": "Entry",
		"spanId": 0,
		"parentSpanId": -1,
		"isError": false,
		"spanLayer": "Http",
		"componentId": 6000
	}],
	"service": "User_Service_Name",
	"traceSegmentId": "a12ff60b-5807-463b-a1f8-fb1c8608219e"
}
```
OutPut:

 ```json

```

### POST http://localhost:12800/v3/segments

Send a segment object list in JSON format.

Input:

```json
[{
	"traceId": "a12ff60b-5807-463b-a1f8-fb1c8608219e",
	"serviceInstance": "User_Service_Instance_Name",
	"spans": [{
		"operationName": "/ingress",
		"startTime": 1588664577013,
		"endTime": 1588664577028,
		"spanType": "Exit",
		"spanId": 1,
		"isError": false,
		"parentSpanId": 0,
		"componentId": 6000,
		"peer": "upstream service",
		"spanLayer": "Http"
	}, {
		"operationName": "/ingress",
		"startTime": 1588664577013,
		"tags": [{
			"key": "http.method",
			"value": "GET"
		}, {
			"key": "http.params",
			"value": "http://localhost/ingress"
		}],
		"endTime": 1588664577028,
		"spanType": "Entry",
		"spanId": 0,
		"parentSpanId": -1,
		"isError": false,
		"spanLayer": "Http",
		"componentId": 6000
	}],
	"service": "User_Service_Name",
	"traceSegmentId": "a12ff60b-5807-463b-a1f8-fb1c8608219e"
}, {
	"traceId": "f956699e-5106-4ea3-95e5-da748c55bac1",
	"serviceInstance": "User_Service_Instance_Name",
	"spans": [{
		"operationName": "/ingress",
		"startTime": 1588664577250,
		"endTime": 1588664577250,
		"spanType": "Exit",
		"spanId": 1,
		"isError": false,
		"parentSpanId": 0,
		"componentId": 6000,
		"peer": "upstream service",
		"spanLayer": "Http"
	}, {
		"operationName": "/ingress",
		"startTime": 1588664577250,
		"tags": [{
			"key": "http.method",
			"value": "GET"
		}, {
			"key": "http.params",
			"value": "http://localhost/ingress"
		}],
		"endTime": 1588664577250,
		"spanType": "Entry",
		"spanId": 0,
		"parentSpanId": -1,
		"isError": false,
		"spanLayer": "Http",
		"componentId": 6000
	}],
	"service": "User_Service_Name",
	"traceSegmentId": "f956699e-5106-4ea3-95e5-da748c55bac1"
}]
```
OutPut:

 ```json

```