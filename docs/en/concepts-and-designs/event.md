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

## How to Configure Alarms for Events

Events derive from metrics, and can be the source to trigger alarms. For example, if a specific event occurs for a
certain times in a period, alarms can be triggered and sent.

Every event has a default `value = 1`, when `n` events with the same name are reported, they are aggregated
into `value = n` as follows.

```
Event{name=Unhealthy, source={service=A,instance=a}, ...}
Event{name=Unhealthy, source={service=A,instance=a}, ...}
Event{name=Unhealthy, source={service=A,instance=a}, ...}
Event{name=Unhealthy, source={service=A,instance=a}, ...}
Event{name=Unhealthy, source={service=A,instance=a}, ...}
Event{name=Unhealthy, source={service=A,instance=a}, ...}
```

will be aggregated into

```
Event{name=Unhealthy, source={service=A,instance=a}, ...} <value = 6>
```

so you can configure the following alarm rule to trigger alarm when `Unhealthy` event occurs more than 5 times within 10
minutes.

```yaml
rules:
  unhealthy_event_rule:
    metrics-name: Unhealthy
    # Healthiness check is usually a scheduled task,
    # they may be unhealthy for the first few times,
    # and can be unhealthy occasionally due to network jitter,
    # please adjust the threshold as per your actual situation.
    threshold: 5
    op: ">"
    period: 10
    count: 1
    message: Service instance has been unhealthy for 10 minutes
```

For more alarm configuration details, please refer to the [alarm doc](../setup/backend/backend-alarm.md).

**Note** that the `Unhealthy` event above is only for demonstration, they are not detected by default in SkyWalking,
however, you can use the methods in [How to Report Events](#how-to-report-events) to report this kind of events.

## Correlation between events and metrics

SkyWalking UI visualizes the events in the dashboard when the event service / instance / endpoint matches the displayed
service / instance / endpoint.

By default, SkyWalking also generates some metrics for events by using [OAL](oal.md). The default metrics list of event
may change over time, you can find the complete list
in [event.oal](../../../oap-server/server-bootstrap/src/main/resources/oal/event.oal). If you want to generate you
custom metrics from events, please refer to [OAL](oal.md) about how to write OAL rules.

## Known Events

| Name | Type | When | Where |
| :----: | :----: | :-----| :---- |
| Start | Normal | When your Java Application starts with SkyWalking Agent installed, the `Start` Event will be created. | Reported from SkyWalking agent. |
| Shutdown | Normal | When your Java Application stops with SkyWalking Agent installed, the `Shutdown` Event will be created. | Reported from SkyWalking agent. |
| Alarm | Error | When the Alarm is triggered, the corresponding `Alarm` Event will is created. | Reported from internal SkyWalking OAP. |

The following events are all reported
by [Kubernetes Event Exporter](http://github.com/apache/skywalking-kubernetes-event-exporter), in order to see these
events, please make sure you have deployed the exporter. 

| Name | Type | When | Where |
| :----: | :----: | :-----| :---- |
| Killing | Normal | When the Kubernetes Pod is being killing. | Reporter by Kubernetes Event Exporter. |
| Pulling | Normal | When a docker image is being pulled for deployment. | Reporter by Kubernetes Event Exporter. |
| Pulled | Normal | When a docker image is pulled for deployment. | Reporter by Kubernetes Event Exporter. |
| Created | Normal | When a container inside a Pod is created. | Reporter by Kubernetes Event Exporter. |
| Started | Normal | When a container inside a Pod is started. | Reporter by Kubernetes Event Exporter. |
| Unhealthy | Error | When the readiness probe failed. | Reporter by Kubernetes Event Exporter. |

The complete event lists can be found
in [the Kubernetes codebase](https://github.com/kubernetes/kubernetes/blob/v1.21.1/pkg/kubelet/events/event.go), please
note that not all the events are supported by the exporter for now.
