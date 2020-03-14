# SkyWalking Cross Process Correlation Headers Protocol
* Version 1.0

Correlation is used to transfer user-defined data in Trace. You can set and get custom information in business code through API.
For example, the execution process of a trace is A -> B -> C, Set transfer content in A service, B and C services can obtain data through API.

## Header Item
* Header Name: `sw7-correlation`
* Header Value: In JSON format, and is `Base64` encoded

Value format example, `{"XX":"XX","XX":"XX"}` with `base64` encode

## Protocol
The Protocol specifies the content of all Correlation data.

1. Each data is generated in Key-Value format, and both values ​​are of type `String`, and multiple Key-Values ​​make up JSON data.
1. Each key/value in JSON only supports the single level.
1. The Key/Value in each data is defined by the user.
1. JSON data needs to be `Base64` encoded.
1. Each key cannot be empty.
1. When each value is empty, please change it to an empty string (like `""`).

## Recommendation of language API
Recommended implementation in different language API.

1. Provide API-level data transmission function, use get or put functions to operate data, they are similar to Map in java.
1. Multiple identical keys will be overwritten and the original old data will be returned to the user.
1. Supports up to 3 key definitions, and the length of the value in each key is 128 bytes. Logging occurs when the size limit is exceeded.
1. The size limit supports modification at the agent configuration level.
1. Data transmission must be performed in the Tracing environment.
1. Data is stored in TracingContext or IgnoreTraceContext.
1. Copy the currently existing data when taking a snapshot, and put the data into a new context when continuing.

## Sample values
1. before base64 encode, `{"userKey1","userValue1","userKey2":"userValue2"}`, after base64 encode: `eyJ1c2VyS2V5MSIsInVzZXJWYWx1ZTEiLCJ1c2VyS2V5MiI6InVzZXJWYWx1ZTIifQ==`
