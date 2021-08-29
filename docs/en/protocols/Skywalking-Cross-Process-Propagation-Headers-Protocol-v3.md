# SkyWalking Cross Process Propagation Headers Protocol
* Version 3.0

SkyWalking is more akin to an APM system, rather than a common distributed tracing system. 
SkyWalking's headers are much more complex than those found in a common distributed tracing system. The reason behind their complexity is for better analysis performance of the OAP. 
You can find many similar mechanisms in other commercial APM systems (some of which are even more complex than ours!).

## Abstract
The SkyWalking Cross Process Propagation Headers Protocol v3, also known as the sw8 protocol, is designed for context propagation.

### Standard Header Item
The standard header is the minimal requirement for context propagation.
* Header Name: `sw8`.
* Header Value: 8 fields split by `-`. The length of header value must be less than 2k (default).

Example of the value format: `XXXXX-XXXXX-XXXX-XXXX`

#### Values
Values must include the following segments, and all string type values are in BASE64 encoding.

- Required:
1. Sample. 0 or 1. 0 means that the context exists, but it could (and most likely will) be ignored. 1 means this trace needs to be sampled and sent to the backend. 
1. Trace ID. **String(BASE64 encoded)**. A literal string that is globally unique.
1. Parent trace segment ID. **String(BASE64 encoded)**. A literal string that is globally unique.
1. Parent span ID. Must be an integer. It begins with 0. This span ID points to the parent span in parent trace segment.
1. Parent service.  **String(BASE64 encoded)**. Its length should be no more than 50 UTF-8 characters.
1. Parent service instance.  **String(BASE64 encoded)**.  Its length should be no more than 50 UTF-8 characters.
1. Parent endpoint. **String(BASE64 encoded)**. The operation name of the first entry span in the parent segment. Its length should be less than 150 UTF-8 characters.
1. Target address of this request used on the client end. **String(BASE64 encoded)**. The network address (not necessarily IP + port) used on the client end to access this target service.

- Sample values:
`1-TRACEID-SEGMENTID-3-PARENT_SERVICE-PARENT_INSTANCE-PARENT_ENDPOINT-IPPORT`

### Extension Header Item
The extension header item is designed for advanced features. It provides interaction capabilities between the agents
deployed in upstream and downstream services.
* Header Name: `sw8-x`
* Header Value: Split by `-`. The fields are extendable.

#### Values
The current value includes fields.
1. Tracing Mode. Empty, 0, or 1. Empty or 0 is the default. 1 indicates that all spans generated in this context will skip analysis,
`spanObject#skipAnalysis=true`. This context is propagated to upstream by default, unless it is changed in the 
tracing process.
2. The timestamp of sending on the client end. This is used in async RPC, such as MQ. Once it is set, the consumer end would calculate the latency between sending and receiving, and tag the latency in the span by using key `transmission.latency` automatically.

