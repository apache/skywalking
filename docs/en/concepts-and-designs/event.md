# Events

SkyWalking already supports the three pillars of observability, namely logs, metrics, and traces.
In reality, a production system experiences many other events that may affect the performance of the system, such as upgrading, rebooting, chaos testing, etc.
Although some of these events are reflected in the logs, there are many other events that can not. Hence, SkyWalking provides a more native way to collect these events.
This doc covers the design of how SkyWalking collects events and what events look like in SkyWalking.

## How to Report Events

SkyWalking backend supports three protocols to collect events, gRPC, HTTP, and Kafka. Any agent or CLI that implements one of these protocols can report events to SkyWalking.
Currently, the officially supported clients to report events are:

- [ ] Java Agent Toolkit: Use the Java agent toolkit to report events from inside the applications.
- [x] SkyWalking CLI: Use the CLI to report events from the command line interface.
- [ ] Kubernetes Event Exporter: Deploy an event exporter to refine and report Kubernetes events.

## Event Definition

An event contains the following fields. The definitions of event can be found at the [protocol repo](https://github.com/apache/skywalking-data-collect-protocol/tree/master/event)

### UUID

Unique ID of the event. Because an event may span a long period of time, the UUID is necessary to associate the start time with the end time of the same event. 

### Source

The source object that the event occurs on. In the concepts of SkyWalking, the object is typically service, service instance, etc.

### Name

The name of the event. For example, `Start`, `Stop`, `Crash`, `Reboot`, `Upgrade` etc.

### Type

The type of the event. This field is friendly for UI visualization, where events of type `Normal` are considered as normal operations,
while `Error` is considered as unexpected operations, such as `Crash` events, therefore we can mark them with different colors to be easier identified.

### Message

The detail of the event that describes why this event happened. This should be a one-line message that briefly describes why the event is reported. Examples of an `Upgrade` event may be something like `Upgrade from ${from_version} to ${to_version}`.
It's NOT encouraged to include the detailed logs of this event, such as the exception stack trace.

### Parameters

The parameters in the `message` field. This is a simple `<string,string>` map. 

### Start Time

The start time of the event. This field is mandatory when an event occurs.

### End Time

The end time of the event. This field may be empty if the event has not stopped yet, otherwise it should be a valid timestamp after `startTime`.

**NOTE:** When reporting an event, you typically call the report function twice, one for starting of the event and the other one for ending of the event, with the same UUID.
There are also cases where you have both the start time and end time already, for example, when exporting events from a 3rd-party system, the start time and end time are already known so that you can call the report function only once.
