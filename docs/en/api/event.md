### Events Report Protocol

The protocol is used to report events to the backend. The [doc](../concepts-and-designs/event.md) introduces the definition of an event, and [the protocol repository](https://github.com/apache/skywalking-data-collect-protocol/blob/master/event) defines gRPC services and message formats of events.

```protobuf
syntax = "proto3";

package skywalking.v3;

option java_multiple_files = true;
option java_package = "org.apache.skywalking.apm.network.event.v3";
option csharp_namespace = "SkyWalking.NetworkProtocol.V3";
option go_package = "skywalking.apache.org/repo/goapi/collect/event/v3";

import "common/Command.proto";

service EventService {
  // When reporting an event, you typically call the collect function twice, one for starting of the event and the other one for ending of the event, with the same UUID.
  // There are also cases where you have both start time and end time already, for example, when exporting events from a 3rd-party system,
  // the start time and end time are already known so that you can call the collect function only once.
  rpc collect (stream Event) returns (Commands) {
  }
}

message Event {
  // Unique ID of the event. Because an event may span a long period of time, the UUID is necessary to associate the
  // start time with the end time of the same event.
  string uuid = 1;

  // The source object that the event occurs on.
  Source source = 2;

  // The name of the event. For example, `Reboot`, `Upgrade` etc.
  string name = 3;

  // The type of the event. This field is friendly for UI visualization, where events of type `Normal` are considered as normal operations,
  // while `Error` is considered as unexpected operations, such as `Crash` events, therefore we can mark them with different colors to be easier identified.
  Type type = 4;

  // The detail of the event that describes why this event happened. This should be a one-line message that briefly describes why the event is reported.
  // Examples of an `Upgrade` event may be something like `Upgrade from ${from_version} to ${to_version}`.
  // It's NOT encouraged to include the detailed logs of this event, such as the exception stack trace.
  string message = 5;

  // The parameters in the `message` field.
  map<string, string> parameters = 6;

  // The start time (in milliseconds) of the event, measured between the current time and midnight, January 1, 1970 UTC.
  // This field is mandatory when an event occurs.
  int64 startTime = 7;

  // The end time (in milliseconds) of the event. , measured between the current time and midnight, January 1, 1970 UTC.
  // This field may be empty if the event has not stopped yet, otherwise it should be a valid timestamp after `startTime`.
  int64 endTime = 8;
  
  // [Required] Since 9.0.0
  // Name of the layer to which the event belongs.
  string layer = 9;
}

enum Type {
  Normal = 0;
  Error = 1;
}

// If the event occurs on a service ONLY, the `service` field is mandatory, the serviceInstance field and endpoint field are optional;
// If the event occurs on a service instance, the `service` and `serviceInstance` are mandatory and endpoint is optional;
// If the event occurs on an endpoint, `service` and `endpoint` are mandatory, `serviceInstance` is optional;
message Source {
  string service = 1;
  string serviceInstance = 2;
  string endpoint = 3;
}
```

`JSON` format events can be reported via HTTP API. The endpoint is `http://<oap-address>:12800/v3/events`.
Example of a JSON event record:
```json
[
    {
        "uuid": "f498b3c0-8bca-438d-a5b0-3701826ae21c",
        "source": {
            "service": "SERVICE-A",
            "instance": "INSTANCE-1"
        },
        "name": "Reboot",
        "type": "Normal",
        "message": "App reboot.",
        "parameters": {},
        "startTime": 1628044330000,
        "endTime": 1628044331000
    }
]
```