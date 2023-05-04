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

* Distributed Tracing
  * End-to-end distributed tracing. Service topology analysis, service-centric observability and APIs dashboards.
* Agents for your stack
  * Java, .Net Core, PHP, NodeJS, Golang, LUA, Rust, C++, Client JavaScript and Python agents with active development and maintenance.
* eBPF early adoption
  * Rover agent works as metrics collector and profiler powered by eBPF to diagnose CPU and network performance.
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

# Documentation
- [Official documentation](https://skywalking.apache.org/docs/#SkyWalking)


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
