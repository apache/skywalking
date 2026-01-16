# Marketplace

The **Marketplace** provides an overview of all out-of-box monitoring features available in SkyWalking. It is also the first UI menu on the SkyWalking native UI.

## Out-of-Box Monitoring Features

SkyWalking provides ready-to-use monitoring capabilities for a wide range of technologies and platforms:

- **General Services** - Application monitoring with language agents (Java, Python, Go, Node.js, PHP, .NET, etc.), including tracing, metrics, and profiling
- **Service Mesh** - Observability for Istio and Envoy-based service meshes through Access Log Service (ALS) or metrics
- **Kubernetes** - Cluster monitoring, pod metrics, and network profiling
- **Infrastructure** - Linux and Windows server monitoring
- **Cloud Services** - AWS EKS, S3, DynamoDB, API Gateway, and more
- **Gateways** - Nginx, APISIX, Kong monitoring
- **Databases** - MySQL, PostgreSQL, Redis, Elasticsearch, MongoDB, ClickHouse, and more
- **Message Queues** - Kafka, RabbitMQ, Pulsar, RocketMQ, ActiveMQ
- **Browser** - Real user monitoring for web applications
- **Self Observability** - Monitor SkyWalking OAP, Satellite, and agents themselves

## How It Works

1. Browse the Marketplace sections in this documentation to find your target technology
2. Follow the setup guide to configure the required agents, receivers, or integrations
3. Once SkyWalking detects services of the configured type, the corresponding menu and dashboards will appear automatically in the UI

Each monitoring feature comes with pre-built dashboards, metrics, and alerting capabilities.

## Advanced: Custom Dashboards

For users who need to visualize custom metrics or create specialized dashboards beyond the out-of-box features:

1. Set up data collection following the Tracing, Logging, or Metrics documentation
2. Configure custom analysis pipelines using the Customization documentation
3. Create custom UI dashboards following the [UI Customization documentation](../../ui/README.md)

