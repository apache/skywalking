Apache SkyWalking
==========

<img src="http://skywalking.apache.org/assets/logo.svg" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: an APM(application performance monitor) system, especially designed for
microservices, cloud native and container-based architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm.svg)](http://skywalking.apache.org/downloads/)
[![CI/IT Tests](https://github.com/apache/skywalking/workflows/CI%20AND%20IT/badge.svg?branch=master)](https://github.com/apache/skywalking/actions?query=workflow%3ACI%2BAND%2BIT+event%3Aschedule+branch%3Amaster)
[![E2E Tests](https://github.com/apache/skywalking/workflows/E2E/badge.svg?branch=master)](https://github.com/apache/skywalking/actions?query=branch%3Amaster+event%3Aschedule+workflow%3AE2E)

# Abstract
**SkyWalking** is an open source APM system, including monitoring, tracing, diagnosing capabilities for distributed system
in Cloud Native architecture.
The core features are following.

- Service, service instance, endpoint(URI) metrics analysis.
  - Distributed tracing.
  - Metrics collecting, analysis, and customization.
  - Logging collecting and extracting metrics from them.
  - Distributed tracing and context propagation.
  - Collaboration across metrics, traces, and logs
- Root cause analysis.
  - Profile the code on the runtime powered by in-process agent and ebpf profiler.
  - Network profiler for TCP, TCP/TLS, HTTP(s) traffic.
- Service topology map analysis.
- Service instance and endpoint(URI) dependency analysis.
- Slow services and endpoints detecting.
- Performance optimization.
- In memory and remote cache performance monitoring, including Read and Write performance. Detect slow Cache access with keys. 
- Database performance monitoring. Detect slow SQL statements.
- Message Queue performance and consuming latency monitoring.
- Browser performance monitoring, and start tracing from browser.
- Infrastructure monitoring. Kubernetes and Linux(VM, network, disk etc.).
- Alerting.

<img src="https://skywalking.apache.org/images/home/architecture.svg?t=20220513"/>

SkyWalking supports to collect telemetry (metrics, traces, and logs) data from multiple sources
and multiple formats, including
1. Java, .NET Core, NodeJS, PHP, and Python auto-instrument agents.
2. Go, C++, and Rust SDKs.
3. [Agent profiling](https://skywalking.apache.org/docs/main/next/en/concepts-and-designs/sdk-profiling/) for Java and Python.
4. [ebpf](https://github.com/apache/skywalking-rover) network profiling and ON/OFF CPU profiling.
5. LUA agent especially for Nginx, OpenResty and Apache APISIX.
6. Browser agent.
7. Service Mesh Observability. Control plane and data plane.
8. Metrics system, including Prometheus, OpenTelemetry, Micrometer(Spring Sleuth), Zabbix.
9. Logs, including OpenTelemetry.
10. Zipkin v1/v2 trace.(No Analysis).

# Documentation
- [Official documentation](https://skywalking.apache.org/docs/#SkyWalking)

SkyWalking OAP is using the STAM(Streaming Topology Analysis Method) to analysis topology in the tracing based agent scenario
for better performance. Read [the paper of STAM](https://wu-sheng.github.io/STAM/) for more details.

# Downloads
Please head to the [releases page](https://skywalking.apache.org/downloads/) to download a release of Apache SkyWalking.

# Compiling project
Follow this [document](docs/en/guides/How-to-build.md).

# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](https://www.apache.org/foundation/policies/conduct). By participating, you are expected to uphold this code.
Please follow the [REPORTING GUIDELINES](https://www.apache.org/foundation/policies/conduct#reporting-guidelines) to report unacceptable behavior.

# Live Demo
- Find the [live demo](https://skywalking.apache.org/#demo) and [screenshots](https://skywalking.apache.org/#arch) on our website.
- Follow the [showcase](https://skywalking.apache.org/docs/skywalking-showcase/next/readme/) to set up preview deployment quickly.

# Contact Us
* Mail list: **dev@skywalking.apache.org**. Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list.
* Send `Request to join SkyWalking slack` mail to the mail list(`dev@skywalking.apache.org`), we will invite you in.
* Twitter, [ASFSkyWalking](https://twitter.com/AsfSkyWalking)
* QQ Group: 901167865, 392443393
* [bilibili B站 视频](https://space.bilibili.com/390683219)

# Our Users
Hundreds of companies and organizations use SkyWalking for research, production, and commercial product.
Visit our [website](http://skywalking.apache.org/users/) to find the user page.

# License
[Apache 2.0 License.](LICENSE)
