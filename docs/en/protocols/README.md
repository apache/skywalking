# Probe Protocols
Probe protocols describe and define how agents send collected metrics, logs, traces, and events, as well as set out the format of each entity.

### Tracing
There are two types of protocols that help language agents work in distributed tracing.

- **Cross Process Propagation Headers Protocol** and **Cross Process Correlation Headers Protocol** come in in-wire data format. Agent/SDK usually uses HTTP/MQ/HTTP2 headers to carry the data with the RPC request. The remote agent will receive this in the request handler, and bind the context with this specific request.

[Cross Process Propagation Headers Protocol v3](Skywalking-Cross-Process-Propagation-Headers-Protocol-v3.md) has been the new protocol for in-wire context propagation since the version 8.0.0 release.

[Cross Process Correlation Headers Protocol v1](Skywalking-Cross-Process-Correlation-Headers-Protocol-v1.md) is a new in-wire context propagation protocol which is additional and optional.
Please read SkyWalking language agents documentation to see whether it is supported.

- **Trace Data Protocol** is an out-of-wire data format. Agent/SDK uses this to send traces to SkyWalking OAP server.

[SkyWalking Trace Data Protocol v3.1](Trace-Data-Protocol-v3.md) defines the communication method and format between the agent and backend.

### Logging
- **Log Data Protocol** is an out-of-wire data format. Agent/SDK and collector use this to send logs into SkyWalking OAP server.
[SkyWalking Log Data Protocol](Log-Data-Protocol.md) defines the communication method and format between the agent and backend.

### Metrics

SkyWalking has a native metrics format, and supports widely used metric formats, such as Prometheus, OpenCensus, OpenTelemetry, and Zabbix.

The native metrics format definition could be found [here](https://github.com/apache/skywalking-data-collect-protocol/blob/master/language-agent/Meter.proto).
The agent meter plugin (e.g. [Java Meter Plugin](https://skywalking.apache.org/docs/skywalking-java/next/en/setup/service-agent/java-agent/java-plugin-development-guide/#meter-plugin)) uses the
native metric format to report metrics.

OpenTelemetry collector, Telegraf agents, Zabbix agents could use their native protocol(e.g. OTLP)
and OAP server would convert metrics into native format and forward them to [MAL](../concepts-and-designs/mal.md) engine.

To learn more about receiving 3rd party formats metrics, see [Meter receiver](../setup/backend/backend-meter.md) and [OpenTelemetry receiver](../setup/backend/opentelemetry-receiver.md).

### Browser probe protocol

The browser probe, such as  [skywalking-client-js](https://github.com/apache/skywalking-client-js), could use this protocol to send data to the backend. This service is provided by gRPC.

[SkyWalking Browser Protocol](Browser-Protocol.md) defines the communication method and format between `skywalking-client-js` and backend.

### Events Report Protocol

The protocol is used to report events to the backend. The [doc](../concepts-and-designs/event.md) introduces the definition of an event, and [the protocol repository](https://github.com/apache/skywalking-data-collect-protocol/blob/master/event) defines gRPC services and message formats of events.

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
