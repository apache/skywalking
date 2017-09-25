Sky Walking | [中文](README_ZH.md)
==========

<img src="https://sky-walking.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking 3**: APM for Distributed Systems, also known Distributed Tracing System. 

[![Build Status](https://travis-ci.org/wu-sheng/sky-walking.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking)
[![Coverage Status](https://coveralls.io/repos/github/wu-sheng/sky-walking/badge.svg?branch=master&forceUpdate=2)](https://coveralls.io/github/wu-sheng/sky-walking?branch=master)
![license](https://img.shields.io/aur/license/yaourt.svg)
[![codebeat badge](https://codebeat.co/badges/579e4dce-1dc7-4f32-a163-c164eafa1335)](https://codebeat.co/projects/github-com-wu-sheng-sky-walking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/sky-walking/Lobby.svg)](https://gitter.im/sky-walking/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)


* Auto instrumentation by javaagent, **no need to CHANGE any application source code**.
  * High performance agent. Only increase extra **10%** cpu cost in 5000+ tps application, even **do not need to use sampling mechanism**, [check test reports](#test-reports).
  * [Supported middlewares, frameworks and libraries](https://github.com/wu-sheng/sky-walking/wiki/3.2-supported-list).
* Manual instrumentation
  * As an [OpenTracing supported tracer](http://opentracing.io/documentation/pages/supported-tracers)
  * Use **@Trace** annotation for any methods you want to trace.
  * Integrate traceId into logs for log4j, log4j2 and logback.
* Pure Java server implementation, provide RESTful and gRPC services. Compatibility with other language agents/SDKs. 
  * [How to uplink metrics and traces to collector?]()
* The UI released on [wu-sheng/sky-walking-ui](https://github.com/wu-sheng/sky-walking-ui)

# Architecture
* Architecture graph for 3.2+
<img src="https://sky-walking.github.io/page-resources/3.2/architecture/3.2-architecture.jpg"/>

# Document
* [WIKI](https://github.com/wu-sheng/sky-walking/wiki)

# PMC - Project Management Committee
* 吴晟 [@wu-sheng](https://github.com/wu-sheng) 
* 张鑫 [@ascrutae](https://github.com/ascrutae)
* 彭勇升 [@peng-yongsheng](https://github.com/peng-yongsheng) R&D director，Tydic

# Committer
* 柏杨 [@bai-yang](https://github.com/bai-yang)  Senior Engineer, Alibaba Group.

# Contributors
* [Contributors List](https://github.com/wu-sheng/sky-walking/graphs/contributors)
* A special thanks to the contributors: 徐妍[@TastySummer](https://github.com/TastySummer) and 戴文, you offer huge helps for this project.

This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wu.sheng@foxmail.com.

# Screenshots
- Topological graph of application clusters based on dubbox and [motan](https://github.com/weibocom/motan).
<img src="https://sky-walking.github.io/page-resources/3.2/topological_graph_test_project.png"/>

- Trace query.
<img src="https://sky-walking.github.io/page-resources/3.2/trace_segment.png"/>

- Span detail.
<img src="https://sky-walking.github.io/page-resources/3.0/span.png" />

- Instance Overview.
<img src="https://sky-walking.github.io/page-resources/3.2/instance_health.png"/>

- JVM Detail.
<img src="https://sky-walking.github.io/page-resources/3.2/instance_graph.png"/>

- Services Dependency Tree.
<img src="https://sky-walking.github.io/page-resources/3.2/service_dependency_tree.png"/>

# Test reports
- Automatic integration test reports
  - [Java Agent test report](https://github.com/sky-walking/agent-integration-test-report)
- Performance test reports
  - [Java Agent test report](https://sky-walking.github.io/Agent-Benchmarks/)

# Contact Us
* Submit an issue
* [Gitter](https://gitter.im/sky-walking/Lobby)
* [Google Mailing List](https://groups.google.com/forum/#!forum/skywalking-distributed-tracing-and-apm)
* QQ Group: 392443393

# License
By contributing your code, you agree to license your contribution under the terms of the [sky-walking license](/LICENSE).
