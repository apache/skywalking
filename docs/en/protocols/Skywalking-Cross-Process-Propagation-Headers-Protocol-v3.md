# SkyWalking Cross Process Propagation Headers Protocol
* Version 3.0

SkyWalking is more likely an APM system, rather than the common distributed tracing system. 
The Headers are much more complex than them in order to improving analysis performance of OAP. 
You can find many similar mechanism in other commercial APM systems. (Some are even much more complex than our's)

## Abstract
SkyWalking Cross Process Propagation Headers Protocol v3 is also named as sw8 protocol, which is for context propagation.

### Standard Header Item
The standard header should be the minimal requirement for the context propagation.
* Header Name: `sw8`.
* Header Value: 8 fields split by `-`. The length of header value should be less than 2k(default).

Value format example, `XXXXX-XXXXX-XXXX-XXXX`

#### Values
Values include the following segments, all String type values are in BASE64 encoding.

- Required(s)
1. Sample. 0 or 1. 0 means context exists, but could(most likely will) ignore. 1 means this trace need to be sampled and send to backend. 
1. Trace Id. **String(BASE64 encoded)**. Literal String and unique globally.
1. Parent trace segment Id. **String(BASE64 encoded)**. Literal String and unique globally.
1. Parent span Id. Integer. Begin with 0. This span id points to the parent span in parent trace segment.
1. Parent service.  **String(BASE64 encoded)**. The length should not be less or equal than 50 UTF-8 characters.
1. Parent service instance.  **String(BASE64 encoded)**.  The length should be less or equal than 50 UTF-8 characters.
1. Parent endpoint. **String(BASE64 encoded)**. Operation Name of the first entry span in the parent segment. The length should be less than 150 UTF-8 characters.
1. Target address used at client side of this request. **String(BASE64 encoded)**. The network address(not must be IP + port) used at client side to access this target
service.

- Sample values, 
`1-TRACEID-SEGMENTID-3-PARENT_SERVICE-PARENT_INSTANCE-PARENT_ENDPOINT-IPPORT`

### Extension Header Item
Extension header item is designed for the advanced features. It provides the interaction capabilities between the agents
deployed in upstream and downstream services.
* Header Name: `sw8-x`
* Header Value: Split by `-`. The fields are extendable.

#### Values
The current value includes fields.
1. Tracing Mode. empty, 0 or 1. empty or 0 is default. 1 represents all spans generated in this context should skip analysis,
`spanObject#skipAnalysis=true`. This context should be propagated to upstream in the default, unless it is changed in the 
tracing process.
2. The timestamp of sending at the client-side. This is used in async RPC such as MQ. Once it is set, the consumer side would calculate the latency between sending and receiving, and tag the latency in the span by using key `transmission.latency` automatically.

