# SkyWalking Cross Process Correlation Headers Protocol
* Version 1.0

The Cross Process Correlation Headers Protocol is used to transport custom data by leveraging the capability of [Cross Process Propagation Headers Protocol](Skywalking-Cross-Process-Propagation-Headers-Protocol-v2.md). 

This is an optional and additional protocol for language tracer implementation. All tracer implementation could consider to implement this.
Cross Process Correlation Header key is `sw7-correlation`. The value is (string key)-(string value) table in `Base64` encoded JSON format.

## Value Example
(string key)-(string value) table in `Base64` encoded JSON format.

Original value raw data `{"userKey1","userValue1","userKey2":"userValue2"}` string, the `Base64` encoded value
`eyJ1c2VyS2V5MSIsInVzZXJWYWx1ZTEiLCJ1c2VyS2V5MiI6InVzZXJWYWx1ZTIifQ==`.

## Recommendations of language APIs
Recommended implementation in different language API.

1. `CorrelationContext#set` and `CorrelationContext#get` are recommended to write and read the correlation context, with key/value string.
1. The key should be added if it is inexistence.
1. The later the write should override the prev value.
1. The number of all keys should less than 3, and the length of each value should be less than 128 bytes.
1. The context should be propageted when across thread and across process like do tracing context propagation.


