# SkyWalking Cross Process Propagation Headers Protocol
* Version 2.0

SkyWalking is more likely an APM system, rather than common distributed tracing system. 
The Headers is much more complex than them in order to improving analysis performance of collector. 
You can find many similar mechanism in other commercial APM system. (Some are even much more complex than our's)

## Abstract
SkyWalking Cross Process Propagation Headers Protocol v2 is also named as sw6 protocol, which is for context propagation.

## Differences from v1 
The major differences of v2 and v1, comes from SkyWalking's evolution, including
1. Mesh and languages are not same always, some info in headers should be optional.
1. BASE64 encoding required.
1. Sampling flag is included.

## Header Item
* Header Name: `sw6`
* Header Value: Split by `-`, the parts are following. The length of header value should be less than 2k(default).

Value format example, `XXXXX-XXXXX-XXXX-XXXX`

## Values
Values include the following segments, all String type values are in BASE64 encoding.

- Required(s)
1. Sample. 0 or 1. 0 means context exists, but could(most likely will) ignore. 1 means this trace need to be sampled and send to backend. 
1. Trace Id. **String(BASE64 encoded)**. Three Longs split by `.` to represent the unique id of this trace.
1. Parent trace segment Id. **String(BASE64 encoded)**. Three Longs split by `.` to represent the unique id of parent segment in parent service.
1. Parent span Id. Integer. Begin with 0. This span id points to the parent span in parent trace segment. 
1. Parent service instance Id. Integer. The instance ID of parent service.
1. Entrance service instance Id. Integer. The instance ID of the entrance service. 
1. Target address of this request. **String(BASE64 encoded)**. The network address(not must be IP + port) used at client side to access this target
service. _This value can use exchange/compress collector service to get the id(integer) to represent the string. If you use the string, it must start with `#`, others use integer directly._

- Optional(s)

Optional values could not exist if the agent/SDK haven't those info or the length of header is over the threshold(2k default).  
1. Entry endpoint of the trace. **String(BASE64 encoded)**. 
_This value can use exchange/compress collector service to get the id(integer) to represent the string. If you use the string, it must start with `#`, others use integer directly._
1. Parent endpoint of the parent service. **String(BASE64 encoded)**. 
_This value can use exchange/compress collector service to get the id(integer) to represent the string. If you use the string, it must start with `#`, others use integer directly._

endpoint id = -1 and parent endpoint name is empty mean there is no real parent endpoint. Since 6.6.0

## Sample values
1. Short version, `1-TRACEID-SEGMENTID-3-5-2-IPPORT`
1. Complete version, `1-TRACEID-SEGMENTID-3-5-2-IPPORT-ENTRYURI-PARENTURI`
