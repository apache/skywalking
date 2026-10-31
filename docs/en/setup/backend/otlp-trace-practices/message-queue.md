# Message Queue

The [trace analysis](../trace-analyzer.md) draws a message as two calls that meet at a broker node of the
`OTLP_VIRTUAL_MQ` layer:

```text
producer ──▶ broker ──▶ consumer
```

- A PRODUCER or CLIENT span that carries `messaging.system` draws `producer → broker` with the
  `otlp_service_relation_client_*` metrics and measures the broker with `otlp_mq_access_*`. The topic or queue it names
  in `messaging.destination.name`, or the older `messaging.destination`, is an endpoint of the broker, with
  `otlp_mq_destination_*`.
- A CONSUMER span that carries `messaging.system` draws `broker → consumer` with `otlp_service_relation_server_cpm`,
  and counts as a request of the consumer in its service and endpoint metrics, and in its instance metrics when the
  span reports an instance.

Both applications need `service.layer=OTLP_GENERAL` on their resource for their spans to be analyzed. Nothing else is
built for a message: no edge from the producer straight to the consumer, no instance under the broker, and no broker
metric from the consumer's side.

## How the two sides meet

The producer and the consumer are both clients of the broker, so their spans are not paired as a caller's CLIENT span
and a callee's SERVER span are, and no network alias is learned for a broker, see [Address-named
peers](../../../concepts-and-designs/tal.md#address-named-peers). The two edges meet only where both sides name the
broker with the same string. W3C trace context carries no address, unlike the SkyWalking agents' `sw8` header, so each
span names the broker from its own attributes, by the first one present:

| Span | Broker named by |
|---|---|
| producer | `service.peer.name` or `peer.service`; `server.address` and `server.port` (`net.peer.name` and `net.peer.port`); `peer.address`; the host and port of `url.full` or `http.url`; `messaging.system` |
| consumer | `server.address` and `server.port` (`net.peer.name` and `net.peer.port`); `messaging.system` |

A span that names the broker by none of the others lands on the node named after its messaging system, `kafka` or
`rabbitmq`, so every cluster of one system reached that way is one node.

## What the agents report

As read from the source of the OpenTelemetry Java agent 2.32.0 with its default settings, and of the main branches,
on 2026-10-30, of the OpenTelemetry .NET automatic instrumentation, the RabbitMQ .NET client, the OpenTelemetry
Python and JavaScript contrib instrumentations and the OpenTelemetry Go automatic instrumentation.

### Kafka

The Java agent, the .NET automatic instrumentation, kafkajs, the Go automatic instrumentation for `segmentio/kafka-go`
and kafka-python put no broker address on their Kafka spans, so both sides land on `kafka`. kafkajs puts the broker's
address on its duration metric only, and kafka-python reports the bootstrap servers as `messaging.url` only.
confluent-kafka (Python) names the broker by the first `bootstrap.servers` entry on both sides, so its edges meet
when every client lists the same entry first. The topology therefore shows one `kafka` node shared by all the Kafka
clusters these agents reach, or, with confluent-kafka, one node per first bootstrap entry.

### RabbitMQ

| Agent | Producer's broker | Consumer's broker |
|---|---|---|
| Java agent | `rabbitmq`; the connection's address is reported as `network.peer.address` only, which the rule does not read | `rabbitmq` |
| pika, aio-pika | the connection's `host:port` | the same; a pika consumer reading `channel.consume()` reports no address and lands on `rabbitmq` |
| amqplib | the connection's `host:port` | the same; `messaging.system` is the broker's product name, and without one the spans draw no MQ edge |
| RabbitMQ .NET client | its endpoint's `host:port` | `rabbitmq`, since its `deliver` span carries no address, so the two edges do not meet |

Where both sides report an address, connect producers and consumers through the same address of the broker, such as
one Kubernetes Service, so that they name the same node.

The Java agent also reports the channel calls other than a publish, `queue.declare` and `basic.ack` among them, as
CLIENT spans that carry `messaging.system`, and the RabbitMQ .NET client reports its connection attempts the same way.
The rule draws them as calls to the broker and counts them in its `otlp_mq_access_*`.

## Which consumer span is counted

The rule counts a CONSUMER span as the consumer's request, except one whose operation (`messaging.operation.type`, or
the older `messaging.operation`) is `receive` and that is the root of its trace, and never counts a CLIENT span whose
operation is `receive`, see [Metrics](../trace-analyzer.md#metrics). What the consumer's response time shows depends
on the span its agent reports:

| Consumer span | Counted | Response time | Reported by |
|---|---|---|---|
| `process`, its parent the producer's span | yes | the handling | Java agent; kafkajs `eachMessage`; RabbitMQ .NET client `deliver` |
| `process`, its parent the producer's span, ended by the acknowledgement | yes | until the message is acknowledged or rejected, the channel closes or errors, or the instrumentation times out (`consumeTimeoutMs`, 1 minute by default); with `noAck`, until the callback returns, without what it left running asynchronously | amqplib |
| `receive` around the callback, its parent the producer's span | yes | the handling | pika and aio-pika callbacks |
| `receive` from one fetch's return to the next fetch, its parent the producer's span | yes | the handling between the two fetches | Go automatic instrumentation, `segmentio/kafka-go` |
| `receive` around a blocking `receive()`, its parent the producer's span | yes | the wait for the message, included | Java agent, JMS `MessageConsumer.receive()` |
| `receive` under a span the application opened | yes | the `Consume` call that returned the message, its wait included | .NET automatic instrumentation |
| `receive` that ends before the message is handled, its parent the producer's span | yes | close to 0 | pika `channel.consume()` |
| named `receive` but with no operation attribute, ended before the handling | yes, even as a root | close to 0 | kafka-python |
| `receive` that is the root of its trace | no | | .NET automatic instrumentation in a consume loop with no span around it; the Go automatic instrumentation for a message that carries no trace context; pika and aio-pika when neither the message nor the running code carries one |
| CLIENT `poll` | no | | kafkajs `eachBatch`, whose per-message `process` spans are counted |

confluent-kafka (Python) reports what one `poll()` or `consume()` call returned as a `recv` span with no attributes
and a `process` span under it, which stays open until the next call. Both are counted as requests, and only the
`process` span draws the edge from the broker.
