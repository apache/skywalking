# Events

SkyWalking already supports the three pillars of observability, namely logs, metrics, and traces.
In reality, a production system experiences many other events that may affect the performance of the system, such as upgrading, rebooting, chaos testing, etc.
Although some of these events are reflected in the logs, many others are not. Hence, SkyWalking provides a more native way to collect these events.
This doc details how SkyWalking collects events and what events look like in SkyWalking.

## How to Report Events

The SkyWalking backend supports three protocols to collect events: gRPC, HTTP, and Kafka. Any agent or CLI that implements one of these protocols can report events to SkyWalking.
Currently, the officially supported clients to report events are:

- [ ] Java Agent Toolkit: Using the Java agent toolkit to report events within the applications.
- [x] SkyWalking CLI: Using the CLI to report events from the command line interface.
- [x] [Kubernetes Event Exporter](http://github.com/apache/skywalking-kubernetes-event-exporter): Deploying an event exporter to refine and report Kubernetes events.

## Event Definitions

An event contains the following fields. The definitions of event can be found at the [protocol repo](https://github.com/apache/skywalking-data-collect-protocol/tree/master/event).

### UUID

Unique ID of the event. Since an event may span a long period of time, the UUID is necessary to associate the start time with the end time of the same event. 

### Source

The source object on which the event occurs. In SkyWalking, the object is typically a service, service instance, etc.

### Name

Name of the event. For example, `Start`, `Stop`, `Crash`, `Reboot`, `Upgrade`, etc.

### Type

Type of the event. This field is friendly for UI visualization, where events of type `Normal` are considered normal operations,
while `Error` is considered unexpected operations, such as `Crash` events. Marking them with different colors allows us to more easily identify them.

### Message

The detail of the event that describes why this event happened. This should be a one-line message that briefly describes why the event is reported. Examples of an `Upgrade` event may be something like `Upgrade from ${from_version} to ${to_version}`.
It's NOT recommended to include the detailed logs of this event, such as the exception stack trace.

### Parameters

The parameters in the `message` field. This is a simple `<string,string>` map. 

### Start Time

The start time of the event. This field is mandatory when an event occurs.

### End Time

The end time of the event. This field may be empty if the event has not ended yet, otherwise there should be a valid timestamp after `startTime`.

**NOTE:** When reporting an event, you typically call the report function twice, the first time for starting of the event and the second time for ending of the event, both with the same UUID.
There are also cases where you would already have both the start time and end time. For example, when exporting events from a third-party system, the start time and end time are already known so you may simply call the report function once.
