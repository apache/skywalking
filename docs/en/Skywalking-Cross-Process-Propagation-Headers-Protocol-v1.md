# Skywalking Cross Process Propagation Headers Protocol
* Version 1.0

SkyWalking is more likely an APM system, rather than common distributed tracing system. 
The Headers is much more complex than them in order to improving analysis performance of collector. 
You can find many similar mechanism in other commercial APM system.(Some even much more complex than us)

# Header Item
* Header Name: `sw3`
* Header Value: Split by `|`, the parts are following.

_The header protocol came from SkyWalking 3, back to 2017. So sw3 header name keeps now._ 

## Values
* Trace Segment Id

The trace segment id is the unique id for the part of the distributed trace. Each id is only used in a single thread. The id includes three parts(Long), e.g. `"1.2343.234234234`
  1) The first one represents application instance id, which assigned by collector. (most likely just an integer value, would be helpful in protobuf)
  2) The second one represents thread id. (In Java most likely just an integer value, would be helpful in protobuf)
  3) The third one also has two parts
     1) A timestamp, measured in milliseconds
     2) A seq, in current thread, between 0(included) and 9999(included)

If you are using other language, you can generate your own id, but make sure it is unique and combined by three longs.

* Span Id

An integer, unique in a trace segment. Start with 0;

* Parent Application Instance

The instance id of the parent node, e.g. for a server of RPC, this id is from the client application instance id.

* Entry Application Instance

The instance id of the entry application. e.g. A distributed trace `A->B->C`, the id is from `A`.

* Peer Host

The peer-host/peer-id from client side. e.g. client uses `182.14.39.1:9080` to access server, this ip:port is the peer host.

_This value can use exchange/compress collector service to get the id(integer) to represent the string. If you use the string, it must start with `#`, others use integer directly._

* Entry Span Operation Name of First Trace Segment

The operation name/id of entry span propagates from `Entry Application Instance`.

_This value can use exchange/compress collector service to get the id(integer) to represent the string. If you use the string, it must start with `#`, others use integer directly._

* Entry Span Operation Name of Parent Trace Segment

The operation name/id of entry span propagates from `Parent Application Instance`.

_This value can use exchange/compress collector service to get the id(integer) to represent the string. If you use the string, it must start with `#`, others use integer directly._

* Distributed Trace Id

The distributed trace id of the whole trace, if in a batch process, it comes from the trace of first batch producer. The rule is as same as `Trace Segment Id` with three Longs.

### Sample value
1. `1.2343.234234234|1|1|1|#127.0.0.1:8080|#/portal/|#/testEntrySpan|1.2343.234234234`
1. `1.2343.234234234|1|1|1|#127.0.0.1:8080|#/portal/|1038|1.2343.234234234`