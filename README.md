Apache SkyWalking
==========

<img src="https://skywalkingtest.github.io/page-resources/logo/sw-big-dark-1200.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: an APM(application performance monitor) system, especially designed for 
microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)


[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm-incubating.svg)](http://skywalking.apache.org/downloads/)
[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

# Abstract
**SkyWalking** is an open source APM system, including monitoring, tracing, diagnosing capabilities for distributed system
in Cloud Native architecture. 
The core features are following.

- Service, service instance, endpoint metrics analysis
- Root cause analysis
- Service topology map analysis
- Service, service instance and endpoint dependency analysis
- Slow services and endpoints detected
- Performance optimization
- Distributed tracing and context propagation
- Alarm


<img src="https://skywalkingtest.github.io/page-resources/6-alpha-overview.png"/>

SkyWalking supports to collect telemetry (traces and metrics) data from multiple sources
and multiple formats, 
including 
1. Java, .NET Core and NodeJS auto-instrument agents in SkyWalking format
1. Istio telemetry format
1. Zipkin v1/v2 formats


# Document
- [6.x Documents](docs/README.md). 

```
5.x is still supported by SkyWalking community, and the agent-backend protocol is compatible with 6.x.
You can go to 5.x branch. At there, you have everything you need.
```

- Go to [5.x pages](https://github.com/apache/incubator-skywalking/tree/5.x). Also 5.x document is [here](https://github.com/apache/incubator-skywalking/blob/5.x/docs/README.md).


# Downloads
Please head to the [releases page](http://skywalking.apache.org/downloads/) to download a release of Apache SkyWalking.


# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. 
Please report unacceptable behavior to dev@skywalking.apache.org .

# Live Demo
- Host in Beijing. [goto](http://106.75.237.45:8080/)
  - Username: admin
  - Password: admin

# Screenshot
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-beta2/Dashboard.png"/>


- [See all screenshots](/docs/Screenshots.md)

# Compiling project
Follow this [document](docs/en/guides/How-to-build.md).

# Contact Us
* Submit an [issue](https://github.com/apache/incubator-skywalking/issues)
* Mail list: dev@skywalking.apache.org
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

# Who Uses SkyWalking?
A wide variety of companies and organizations use SkyWalking for research, production and commercial product.
Here is the **User Wall** of SkyWalking.

<img src="https://skywalkingtest.github.io/page-resources/users/users-2018-09-07.png"/>

Users are encouraged to add themselves to the [PoweredBy](docs/powered-by.md) page.

<p align="center">
<a href="https://openapm.io"><img src="https://openapm.io/static/media/openapm_logo.svg" width="100"/></a> 
  <br/>Our project enriches the <a href="https://openapm.io">OpenAPM Landscape!</a>
</p>

# License
[Apache 2.0 License.](/LICENSE)
