Apache SkyWalking | [中文](README_ZH.md)
==========

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: APM (application performance monitor) tool for distributed systems, especially designed for 
microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.
Underlying technology is a distributed tracing system.

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)


[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm-incubating.svg)](http://skywalking.apache.org/downloads/)
[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

* Provide Java agent, **no need to CHANGE any application source code**.
  * High performance agent. 
  * [Supported middlewares, frameworks and libraries](docs/Supported-list.md).
* Manual instrumentation
  * As an [OpenTracing supported tracer](http://opentracing.io/documentation/pages/supported-tracers)
  * Use **@Trace** annotation for any methods you want to trace.
  * Integrate traceId into logs for log4j, log4j2 and logback.
* Pure Java server implementation, provide RESTful and gRPC services. Compatibility with other language agents/SDKs. 
* Community Agents/SDKs
  * [.NET Core](https://github.com/OpenSkywalking/skywalking-netcore)

# Document
[![EN doc](https://img.shields.io/badge/document-English-blue.svg)](docs/README.md) [![cn doc](https://img.shields.io/badge/文档-中文版-blue.svg)](docs/README_ZH.md)

# 5.x Architecture
<img src="https://skywalkingtest.github.io/page-resources/5.0/architecture.png"/>

# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wusheng@apache.org.

# Live Demo
- Host in Beijing. [goto](http://49.4.12.44:8080/)
- Host in HK. [goto](http://159.138.0.181:8080/)

# Screenshots
- Overview dashboard
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/Dashboard.png"/>

- Discovery topological graph of application clusters automatically.
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/Topology.png"/>

- Application overview
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/Application.png"/>

- Server metrics
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/server.png"/>

- Service metrics
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/Service.png"/>

- Trace
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/trace.png"/>

- Alarm
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-alpha/application-alarm.png"/>

# Test reports
- Automatic integration test reports
  - [Java Agent test report](https://github.com/SkywalkingTest/agent-integration-test-report)
- Performance test reports
  - [Java Agent test report](https://skywalkingtest.github.io/Agent-Benchmarks/)

# Contact Us
* Submit an issue
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

# Users
<img src="https://skywalkingtest.github.io/page-resources/users/users-2018-04-18.png"/>

[Submit new user case to us.](https://github.com/apache/incubator-skywalking/issues/443)

# License
[Apache 2.0 License.](/LICENSE)
