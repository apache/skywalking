Apache SkyWalking | [中文](README_ZH.md)
==========

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: APM (application performance monitor) tool for distributed systems, especially designed for 
microservices, cloud native and container-based (Docker, Kubernetes, Mesos) architectures.

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)


[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm-incubating.svg)](http://skywalking.apache.org/downloads/)
[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

# Abstract
**SkyWalking** started as a distributed tracing system in 2015. From 5.x, it evolved to the fully functional [Application Performance Management](https://en.wikipedia.org/wiki/Application_performance_management) 
system. It is used for tracing, monitoring, diagnose distributed systems, especially based on microservices, cloud native and container,
including:
- Distributed tracing and context propagation
- Application, instance, service metrics analysis
- Root cause analysis
- Application topology map analysis
- Application and service dependency analysis
- Slow service detected
- Performance optimization

# Core features
- Multiple languages instrumentation agent or libraries 
  - Native Java auto instrumentation agent. Trace and monitor your cluster without changing any source codes
  - Community instrumentation agent or libraries
    * [.NET Core](https://github.com/OpenSkywalking/skywalking-netcore) 
    * [Node.js](https://github.com/OpenSkywalking/skywalking-nodejs)
- Multiple storage backends: ElasticSearch, H2
- [OpenTracing](http://opentracing.io/) compatible
  - Native Java auto instrumentation agent could work with OpenTracing APIs in the same context
- Lightweight and powerful backend aggregation and analysis capabilities
- Modern and cool Web UI
- Log integration
- Alarm for slow or unstable(low SLA) application, instance and service 

# Document
- [Documents in English](docs/README.md)
- [Documents in Chinese](docs/README_ZH.md)

# 5.x Architecture
<img src="https://skywalkingtest.github.io/page-resources/5.0/architecture.png"/>

# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wusheng@apache.org.

# Live Demo
- Host in Beijing. [goto](http://49.4.12.44:8080/)
- Host in HK. [goto](http://159.138.0.181:8080/)

# Screenshot
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-beta/Dashboard.png"/>


- [See all screenshots](/docs/Screenshots.md)

# Test reports
- Automatic integration test reports
  - [Java Agent test report](https://github.com/SkywalkingTest/agent-integration-test-report)
- Performance test reports
  - [Java Agent test report](https://skywalkingtest.github.io/Agent-Benchmarks/)

# Contact Us
* Submit an issue
* Mail list: dev@skywalking.apache.org
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

# Users
<img src="https://skywalkingtest.github.io/page-resources/users/users-2018-04-18.png"/>

[Submit new user case to us.](https://github.com/apache/incubator-skywalking/issues/443)

# License
[Apache 2.0 License.](/LICENSE)
