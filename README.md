Apache SkyWalking
==========

<img src="http://skywalking.apache.org/assets/logo.svg" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: an APM(application performance monitor) system, especially designed for
microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm.svg)](http://skywalking.apache.org/downloads/)
[![CI/IT Tests](https://github.com/apache/skywalking/workflows/CI%20AND%20IT/badge.svg?branch=master)](https://github.com/apache/skywalking/actions?query=branch%3Amaster+event%3Apush+workflow%3A%22CI+AND+IT%22)
[![E2E Tests](https://github.com/apache/skywalking/workflows/E2E/badge.svg?branch=master)](https://github.com/apache/skywalking/actions?query=branch%3Amaster+event%3Apush+workflow%3AE2E)

# Abstract
**SkyWalking** is an open source APM system, including monitoring, tracing, diagnosing capabilities for distributed system
in Cloud Native architecture.
The core features are following.

- Service, service instance, endpoint metrics analysis
- Root cause analysis. Profile the code on the runtime
- Service topology map analysis
- Service, service instance and endpoint dependency analysis
- Slow services and endpoints detected
- Performance optimization
- Distributed tracing and context propagation
- Database access metrics. Detect slow database access statements(including SQL statements).
- Alarm
- Browser performance monitoring

<img src="http://skywalking.apache.org/assets/frame-v8.jpg?u=20201105"/>

SkyWalking supports to collect telemetry (traces and metrics) data from multiple sources
and multiple formats,
including
1. Java, .NET Core, NodeJS, PHP, and Python auto-instrument agents.
1. Go and C++ SDKs.
1. LUA agent especially for Nginx, OpenResty.
1. Service Mesh Observability. Control panel and data panel. 
1. Metrics system, including Prometheus, OpenTelemetry, Spring Sleuth(Micrometer).
1. Browser application performance, including metrics and error logs.
1. Zipkin v1/v2 and Jaeger gRPC format with limited topology and metrics analysis.(Experimental).

SkyWalking OAP is using the STAM(Streaming Topology Analysis Method) to analysis topology in the tracing based agent scenario 
for better performance. Read [the paper of STAM](https://wu-sheng.github.io/STAM/) for more details.

# Documentation
- [Official documentation](https://skywalking.apache.org/docs/)
- [The paper of STAM](https://wu-sheng.github.io/STAM/), Streaming Topology Analysis Method.
- [Blog](https://skywalking.apache.org/blog/2020-04-13-apache-skywalking-profiling/) about Use Profiling to Fix the Blind Spot of Distributed Tracing
- [Blog](https://skywalking.apache.org/blog/2020-12-03-obs-service-mesh-with-sw-and-als/) about observing Istio + Envoy service mesh with ALS solution.

NOTICE, SkyWalking 8.0+ uses [v3 protocols](docs/en/protocols/README.md). They are incompatible with previous releases.

# Downloads
Please head to the [releases page](https://skywalking.apache.org/downloads/) to download a release of Apache SkyWalking.

# Compiling project
Follow this [document](docs/en/guides/How-to-build.md).

# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code.
Please follow the [REPORTING GUIDELINES](CODE_OF_CONDUCT.md#reporting-guidelines) to report unacceptable behavior.

# Live Demo
Find the [demo](https://skywalking.apache.org/#demo) and [screenshots](https://skywalking.apache.org/#arch) on our website.

**Video on youtube.com**

[![RocketBot UI](http://img.youtube.com/vi/mfKaToAKl7k/0.jpg)](http://www.youtube.com/watch?v=mfKaToAKl7k)

# Contact Us
* Mail list: **dev@skywalking.apache.org**. Mail to `dev-subscribe@skywalking.apache.org`, follow the reply to subscribe the mail list.
* Join `skywalking` channel at [Apache Slack](http://s.apache.org/slack-invite). If the link is not working, find the latest one at [Apache INFRA WIKI](https://cwiki.apache.org/confluence/display/INFRA/Slack+Guest+Invites).
* Twitter, [ASFSkyWalking](https://twitter.com/ASFSkyWalking)
* QQ Group: 901167865(Recommended), 392443393
* [bilibili B站 视频](https://space.bilibili.com/390683219)

# Our Users
Hundreds of companies and organizations use SkyWalking for research, production, and commercial product.
Visit our [website](http://skywalking.apache.org/#users) to find the user page.


# Landscapes

<p align="center">
<br/><br/>
<img src="https://landscape.cncf.io/images/left-logo.svg" width="150"/>&nbsp;&nbsp;<img src="https://landscape.cncf.io/images/right-logo.svg" width="200"/>
<br/><br/>
SkyWalking enriches the <a href="https://landscape.cncf.io/landscape=observability-and-analysis&license=apache-license-2-0">CNCF CLOUD NATIVE Landscape.

</p>

<p align="center">
<a href="https://openapm.io"><img src="https://openapm.io/static/media/openapm_logo.svg" width="100"/></a>
  <br/>Our project enriches the <a href="https://openapm.io">OpenAPM Landscape!</a>
</p>

# License
[Apache 2.0 License.](/LICENSE)
