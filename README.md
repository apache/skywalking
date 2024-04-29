Apache SkyWalking
==========

<img src="http://skywalking.apache.org/assets/logo.svg" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: an APM (Application Performance Monitoring) system, especially designed for
microservices, cloud native and container-based architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm.svg)](http://skywalking.apache.org/downloads/)

# Abstract
**SkyWalking** is an open-source APM system that provides monitoring, tracing and diagnosing capabilities for distributed systems in Cloud Native architectures.


* Distributed Tracing
  * End-to-end distributed tracing. Service topology analysis, service-centric observability and APIs dashboards.
* Agents for your stack
  * Java, .Net Core, PHP, NodeJS, Golang, LUA, Rust, C++, Client JavaScript and Python agents with active development and maintenance.
* eBPF early adoption
  * Rover agent works as a monitor and profiler powered by eBPF to monitor Kubernetes deployments and diagnose CPU and network performance.
* Scaling
  * 100+ billion telemetry data could be collected and analyzed from one SkyWalking cluster.
* Mature Telemetry Ecosystems Supported
  * Metrics, Traces, and Logs from mature ecosystems are supported, e.g. Zipkin, OpenTelemetry, Prometheus, Zabbix, Fluentd
* Native APM Database
  * BanyanDB, an observability database, created in 2022, aims to ingest, analyze and store telemetry/observability data.
* Consistent Metrics Aggregation
  * SkyWalking native meter format and widely known metrics format(OpenTelemetry, Telegraf, Zabbix, e.g.) are processed through the same script pipeline.
* Log Management Pipeline
  * Support log formatting, extract metrics, various sampling policies through script pipeline in high performance.
* Alerting and Telemetry Pipelines
  * Support service-centric, deployment-centric, API-centric alarm rule setting. Support forwarding alarms and all telemetry data to 3rd party.

<img src="https://skywalking.apache.org/images/home/architecture.svg?t=20220513"/>

# Live Demo
- Find the [SkyWalking live demo with native UI and Grafana](https://skywalking.apache.org/#demo), and [screenshots](https://skywalking.apache.org/#arch) on our website.
- Follow the [showcase](https://skywalking.apache.org/docs/skywalking-showcase/next/readme/) to set up a preview deployment quickly.

# Documentation
- [Official documentation](https://skywalking.apache.org/docs/#SkyWalking)

# Downloads
Please head to the [releases page](https://skywalking.apache.org/downloads/) to download a release of Apache SkyWalking.

# Compiling project
Follow this [document](docs/en/guides/How-to-build.md).

# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](https://www.apache.org/foundation/policies/conduct). By participating, you are expected to uphold this code.
Please follow the [REPORTING GUIDELINES](https://www.apache.org/foundation/policies/conduct#reporting-guidelines) to report unacceptable behavior.

# Contact Us
* Mail list: **dev@skywalking.apache.org**. Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list.
* Send `Request to join SkyWalking slack` mail to the mail list(`dev@skywalking.apache.org`), we will invite you in.
* For Chinese speaker, send `[CN] Request to join SkyWalking slack` mail to the mail list(`dev@skywalking.apache.org`), we will invite you in.
* Twitter, [ASFSkyWalking](https://twitter.com/AsfSkyWalking)
* [bilibili B站 视频](https://space.bilibili.com/390683219)
* [掘金](https://juejin.cn/user/13673577331607/posts)
  
# Our Users
Hundreds of companies and organizations use SkyWalking for research, production, and commercial purposes.
Visit our [website](http://skywalking.apache.org/users/) to find the user page.

# License
[Apache 2.0 License.](LICENSE)
