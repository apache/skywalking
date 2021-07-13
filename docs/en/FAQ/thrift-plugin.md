### Problem
The message with Field ID, 8888, must be reserved.

### Reason
Because Thrift cannot carry metadata to transport Trace Header in the original API, we transport them by wrapping TProtocolFactory.

Thrift allows us to append any additional fields in the message even if the receiver doesn't deal with them. Those data will be skipped and left unread. Based on this, the 8888th field of the message is used to store Trace Header (or metadata) and to transport them. That means the message with Field ID, 8888, must be reserved.

### Resolution
Avoid using the Field(ID is 8888) in your application.
