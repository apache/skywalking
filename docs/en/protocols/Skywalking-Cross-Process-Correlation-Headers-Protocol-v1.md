# SkyWalking Cross Process Correlation Headers Protocol
* Version 1.0

The Cross Process Correlation Headers Protocol is used to transport custom data by leveraging the capability of [Cross Process Propagation Headers Protocol](Skywalking-Cross-Process-Propagation-Headers-Protocol-v3.md). 

This is an optional and additional protocol for language tracer implementation. All tracer implementation could consider to implement this.
Cross Process Correlation Header key is `sw8-correlation`. The value is the `encoded(key):encoded(value)` list with elements splitted by `,` such as `base64(string key):base64(string value),base64(string key2):base64(string value2)`.

## Recommendations of language APIs
Recommended implementation in different language API.

1. `TraceContext#putCorrelation` and `TraceContext#getCorrelation` are recommended to write and read the correlation context, with key/value string.
1. The key should be added if it is absent.
1. The later writes should override the previous value.
1. The total number of all keys should be less than 3, and the length of each value should be less than 128 bytes.
1. The context should be propagated as well when tracing context is propagated across threads and processes.
