Apache SkyWalking
==========

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: an observability analysis platform, also an APM(application performance monitor) system, especially designed for 
microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)


[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm-incubating.svg)](http://skywalking.apache.org/downloads/)
[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

# Abstract
**SkyWalking** provides the observability and analysis platform, including monitoring, tracing, diagnosing capabilities for distributed system.

- Service, service instance, endpoint metrics analysis
- Root cause analysis
- Service topology map analysis
- Service, service instance and endpoint dependency analysis
- Slow services and endpoints detected
- Performance optimization
- Distributed tracing and context propagation
- Alarm

<img src="https://skywalkingtest.github.io/page-resources/6_overview.png"/>


SkyWalking provides multiple probes to collect data from different sources.
- SkyWalking native instrument agents or SDKs
  - Auto instrument agents
    * Java. Included in official release.
    * [.NET Core](https://github.com/OpenSkywalking/skywalking-netcore) 
    * [Node.js](https://github.com/OpenSkywalking/skywalking-nodejs)
  - SDKs
    * OpenCensus with SkyWalking exporter.
- Service mesh control panel or data panel probes.
  - Istio mixer adaptor.
  - Linkerd.
- 3rd party data tracing format.
  - Zipkin JSON, Thrift, Protobuf v1 and v2 formats, powered by [OpenZipkin](https://github.com/openzipkin/zipkin) libs
  - Jaeger in [Zipkin Thrift or JSON v1/v2 formats](https://github.com/jaegertracing/jaeger#backwards-compatibility-with-zipkin).

# Recommend and typical usages
1. All service are under SkyWalking native agents or SDKs monitoring.
2. Service mesh probes.
3. Service mesh probes + distributed tracing(SkyWalking's or 3rd-party's).


# Document
- [Documents in English](docs/README.md)


# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wusheng@apache.org.

# Live Demo
- Host in Beijing. [goto](http://49.4.12.44:8080/)

# Screenshot
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-beta/Dashboard.png"/>


- [See all screenshots](/docs/Screenshots.md)

# Compiling project
Follow this [document](https://github.com/apache/incubator-skywalking/blob/master/docs/en/How-to-build.md).

# Contact Us
* Submit an issue
* Mail list: dev@skywalking.apache.org
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

# Users
<img src="https://skywalkingtest.github.io/page-resources/users/users-2018-06-07.png"/>

[Submit new user case to us.](https://github.com/apache/incubator-skywalking/issues/443)

# License
[Apache 2.0 License.](/LICENSE)
