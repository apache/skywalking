# SkyWalking Cross Process Propagation Headers Protocol
* Version 3.0

SkyWalking is more likely an APM system, rather than common distributed tracing system. 
The Headers is much more complex than them in order to improving analysis performance of collector. 
You can find many similar mechanism in other commercial APM system. (Some are even much more complex than our's)

## Abstract
SkyWalking Cross Process Propagation Headers Protocol v2 is also named as sw6 protocol, which is for context propagation.

## Header Item
* Header Name: `sw8`
* Header Value: Split by `-`, the parts are following. The length of header value should be less than 2k(default).

Value format example, `XXXXX-XXXXX-XXXX-XXXX`

## Values
Values include the following segments, all String type values are in BASE64 encoding.

- Required(s)
1. Sample. 0 or 1. 0 means context exists, but could(most likely will) ignore. 1 means this trace need to be sampled and send to backend. 
1. Trace Id. **String(BASE64 encoded)**. Three Longs split by `.` to represent the unique id of this trace.
1. Parent trace segment Id. **String(BASE64 encoded)**. Three Longs split by `.` to represent the unique id of parent segment in parent service.
1. Parent span Id. Integer. Begin with 0. This span id points to the parent span in parent trace segment.
1. Parent service.  **String(BASE64 encoded)**. 
1. Parent service instance.  **String(BASE64 encoded)**.
1. Parent endpoint. **String(BASE64 encoded)**.
1. Target address used at client side of this request. **String(BASE64 encoded)**. The network address(not must be IP + port) used at client side to access this target
service.

## Sample values
`1-TRACEID-SEGMENTID-3-PARENT_SERVICE-PARENT_INSTANCE-PARENT_ENDPOINT-IPPORT`


## Differences from v2
All ID register mechanism has been removed. Agent keeps using literal string to propagate all necessary information.
[SkyWalking v2](https://github.com/apache/skywalking/blob/v7.0.0/docs/en/protocols/Trace-Data-Protocol-v2.md) 

## Differences from v1 
The major differences of v2 and v1, comes from SkyWalking's evolution, including
1. Mesh and languages are not same always, some info in headers should be optional.
1. BASE64 encoding required.
1. Sampling flag is included.
