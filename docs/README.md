# Welcome

**This is the official documentation of SkyWalking 10. Welcome to the SkyWalking community!**

SkyWalking is an open source observability platform for collecting, analyzing, aggregating, and visualizing data from services and cloud native infrastructures. It provides distributed tracing, service mesh telemetry analysis, metrics aggregation, alerting, and visualization capabilities.

## Documentation Structure

### Concepts and Designs
Understand the core architecture, terminology, and design principles of SkyWalking. Start with the [Overview](en/concepts-and-designs/overview.md).

### Setup
Installation and configuration guides for different deployment scenarios.

- **Quick Start** - Get SkyWalking running with [Docker](en/setup/backend/backend-docker.md) or [Kubernetes](en/setup/backend/backend-k8s.md)
- **Marketplace** - Explore all [out-of-box monitoring features](en/setup/backend/marketplace.md) for services, service mesh, Kubernetes, databases, message queues, and more
- **Agent Compatibility** - Check [agent compatibility](en/setup/service-agent/agent-compatibility.md) for SkyWalking language agents
- **Advanced Setup** - Storage options, cluster management, security, and dynamic configuration

### APIs
Protocol specifications for integration, including [Telemetry APIs](en/api/trace-data-protocol-v3.md) for reporting data and [Query APIs](en/api/query-protocol.md) for retrieving data.

### Customization
Extend SkyWalking with custom analysis pipelines using [Observability Analysis Language](en/concepts-and-designs/oal.md), [Meter Analysis Language](en/concepts-and-designs/mal.md), and [Log Analysis Language](en/concepts-and-designs/lal.md).

### Security
[Suggestions](en/security/README.md) for keeping your SkyWalking deployment secure. For reporting security vulnerabilities, please follow the [ASF Security Policy](https://www.apache.org/security/).

### Academy
In-depth articles and papers about SkyWalking architecture and best practices.

### FAQs
[Solutions](en/FAQ/README.md) to common issues with setup and development.

### Contributing Guides
For contributors and committers - [contact the community](en/guides/community.md), learn how to [build](en/guides/How-to-build.md) and [test](en/guides/e2e.md) the project. For major features, see [SkyWalking Improvement Proposals](en/swip/readme.md).

### Changelog
Release notes and version history. See [current version](en/changes/changes.md) or browse all versions in the documentation menu.

## Additional Resources

- [Apache SkyWalking Downloads](https://skywalking.apache.org/downloads/) - Official releases
- [SkyWalking WIKI](https://cwiki.apache.org/confluence/display/SKYWALKING/Home) - Additional context and events
- [Community Resources](https://github.com/OpenSkywalking/Community) - Conference schedules, videos, and articles
- [SkyWalking CLI](https://github.com/apache/skywalking-cli) - Command line interface

## Getting Help

- **Questions & Answers** - Post to [GitHub Discussions](https://github.com/apache/skywalking/discussions)
- **Bug Reports** - File an [issue](https://github.com/apache/skywalking/issues/new) directly if you're certain it's a bug
- **Slack Channels** - Join `#skywalking` for English or `#skywalking-cn` for Chinese discussions. To get an invite, send a request to dev@skywalking.apache.org

We're always looking for help to improve our documentation and code. Feel free to contribute by submitting a pull request!
