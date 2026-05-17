# Marketplace

The **Marketplace** is SkyWalking's catalog of out-of-box monitoring
capabilities — the technologies, platforms, and protocols the OAP backend
already knows how to receive, analyze, and expose as metrics, traces, logs,
and entities.

As of 11.0.0 the OAP backend ships only the **data plane** of the
marketplace: receivers, analyzers, metrics definitions, layer entities,
and the query surfaces (GraphQL / MQE / REST) that expose them. The
**visualization layer** — dashboards, the marketplace UI, sidebar menu,
i18n — lives in
[apache/skywalking-horizon-ui](https://github.com/apache/skywalking-horizon-ui)
and ships independently.

In short:

| | OAP backend (this repo) | Horizon UI (separate repo) |
|---|---|---|
| Metrics, traces, logs, entities, layers | ✓ | — |
| Receivers, analyzers, OAL / MAL / LAL rules | ✓ | — |
| GraphQL / MQE / admin REST query surfaces | ✓ | — |
| Dashboards, widgets, marketplace UI | — | ✓ |
| Sidebar menu, i18n keys, layout templates | — | ✓ |

## Out-of-Box Monitoring Features

The OAP backend provides metric, trace, and entity coverage for the
following technology layers. Each layer entry below is a *data* contract —
the matching dashboard / visualization lives in Horizon UI.

- **General Services** - Application monitoring with language agents (Java, Python, Go, Node.js, PHP, .NET, etc.), including tracing, metrics, and profiling
- **Service Mesh** - Observability for Istio and Envoy-based service meshes through Access Log Service (ALS) or metrics
- **Kubernetes** - Cluster monitoring, pod metrics, and network profiling
- **Infrastructure** - Linux and Windows server monitoring
- **Cloud Services** - AWS EKS, S3, DynamoDB, API Gateway, and more
- **Gateways** - Nginx, APISIX, Kong monitoring
- **GenAI** - [Virtual GenAI](../service-agent/virtual-genai.md) for agent-based LLM call monitoring, [Envoy AI Gateway](backend-envoy-ai-gateway-monitoring.md) for infrastructure-side AI traffic observability
- **Databases** - MySQL, PostgreSQL, Redis, Elasticsearch, MongoDB, ClickHouse, and more
- **Message Queues** - Kafka, RabbitMQ, Pulsar, RocketMQ, ActiveMQ
- **Browser** - Real user monitoring for web applications
- **Self Observability** - Monitor SkyWalking OAP, Satellite, and agents themselves

## How It Works

1. Browse the marketplace sections in this documentation to find your target technology.
2. Follow the setup guide to configure the required agents, receivers, or integrations on the OAP backend.
3. Once OAP detects services / instances / endpoints of the configured layer, the corresponding entities show up under `listServices(layer: ...)` and the metrics light up for query.
4. Horizon UI auto-discovers the new layer through its own dashboard library and renders the matching menu entries / dashboards. Dashboard layouts and i18n updates ship from the Horizon UI repository on its own release cadence.

Each layer comes with pre-defined metrics (OAL / MAL), trace shapes, and
alerting templates on the OAP side; dashboards that render those metrics
ship from Horizon UI.

## Advanced: Custom Metrics

For users who need to extend SkyWalking beyond the out-of-box features:

1. Set up data collection following the Tracing, Logging, or Metrics
   documentation.
2. Configure custom analysis pipelines using the Customization
   documentation (custom OAL, MAL, LAL).
3. Push custom dashboards through OAP's UI Management REST surface
   ([UI Management API](admin-api/ui-management.md)) or contribute
   them upstream to [Horizon UI](https://github.com/apache/skywalking-horizon-ui).

See [UI Customization](../../ui/README.md) for the dashboard-side workflow.
