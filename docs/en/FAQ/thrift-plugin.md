### Problem
The message with Field ID, 8888, must be revered.

### Reason
Because Thrift cannot carry metadata to transport Trace Header in the original API, we transport those by wrapping TProtocolFactory to do that.

Thrift allows us to append any additional field in the Message even if the receiver doesn't deal with them. This data is going to be skipped while no one reads. Base on this, we take the 8888th field of Message to store Trace Header(or metadata) and to transport. That means the message with Field ID, 8888, must be revered.

### Resolve
Avoiding to use the Field(ID is 8888) in your application.