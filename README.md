Apache SkyWalking | [中文](README_ZH.md)
==========

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: APM for Distributed Systems, also known Distributed Tracing System. 

[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Coverage Status](https://coveralls.io/repos/github/apache/incubator-skywalking/badge.svg?branch=master&u=1)](https://coveralls.io/github/apache/incubator-skywalking?branch=master)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

* Provide Java agent, **no need to CHANGE any application source code**.
  * High performance agent. Only increase extra **10%** cpu cost in 5000+ tps application, even **when collect all traces**.
  * [Supported middlewares, frameworks and libraries](docs/Supported-list.md).
* Manual instrumentation
  * As an [OpenTracing supported tracer](http://opentracing.io/documentation/pages/supported-tracers)
  * Use **@Trace** annotation for any methods you want to trace.
  * Integrate traceId into logs for log4j, log4j2 and logback.
* Pure Java server implementation, provide RESTful and gRPC services. Compatibility with other language agents/SDKs. 
* The UI released on [skywalking-ui](https://github.com/apache/incubator-skywalking-ui)

# Architecture
* Architecture graph for 3.2.5+
<img src="https://skywalkingtest.github.io/page-resources/3.2.5%2b_architecture.jpg"/>

# Document
[![EN doc](https://img.shields.io/badge/document-English-blue.svg)](docs/README.md) [![cn doc](https://img.shields.io/badge/document-中文-blue.svg)](docs/README_ZH.md)

This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wu.sheng@foxmail.com.

# Screenshots
- Discovery topological graph of application clusters automatically.
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/topological_graph_test_project.png"/>

- Trace query.
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/trace_segment.png"/>

- Span detail.
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/span.png" />

- Instance Overview.
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/instance_health.png"/>

- JVM Detail.
<img src="https://skywalkingtest.github.io/page-resources/3.2/instance_graph.png"/>

- Services Dependency Tree.
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/service_dependency_tree.png"/>

# Test reports
- Automatic integration test reports
  - [Java Agent test report](https://github.com/SkywalkingTest/agent-integration-test-report)
- Performance test reports
  - [Java Agent test report](https://skywalkingtest.github.io/Agent-Benchmarks/)

# Contact Us
* Submit an issue
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ Group: 392443393

# License
[Apache 2.0 License.](/LICENSE)
