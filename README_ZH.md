Apache SkyWalking | [English](README.md)
==========

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: 针对分布式系统的APM（应用性能监控）系统，特别针对微服务、cloud native和容器化(Docker, K8s, Mesos)架构，
其核心是个分布式追踪系统。

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)

[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/openskywalking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)


* Java自动探针，**不需要修改应用程序源代码**
  * 高性能探针，针对单实例5000tps的应用，在**全量采集的情况下**，只增加**10%**的CPU开销。
  * [中间件，框架与类库支持列表](docs/Supported-list.md).
* 手动探针
  * [使用OpenTracing手动探针API](http://opentracing.io/documentation/pages/supported-tracers)
  * 使用 [**@Trace**](docs/cn/Application-toolkit-trace-CN.md) 标注追踪业务方法
  * 将 traceId 集成到 log4j, log4j2 或 logback这些日志组件中
* 纯Java后端Collector实现，提供RESTful和gRPC接口。兼容接受其他语言探针发送数据 
  * [如何将探针的Metric和Trace数据上传到Collector？](/docs/cn/How-to-communicate-with-the-collector-CN.md)
* UI工程请查看 [skywalking-ui](https://github.com/apache/incubator-skywalking-ui)
* 中文QQ群：392443393

# Document
[![EN doc](https://img.shields.io/badge/document-English-blue.svg)](docs/README.md) [![cn doc](https://img.shields.io/badge/文档-中文版-blue.svg)](docs/README_ZH.md)


# 5.x Architecture
<img src="https://skywalkingtest.github.io/page-resources/5.0/architecture.png"/>

# code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wusheng@apache.org.

# Screenshots
- 分布式系统拓扑图自动发现
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/topological_graph_test_project.png?forceUpdate=0"/>

- 调用链查询
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/trace_segment.png"/>

- Span信息查询
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/span.png" />

- 实例全局视图
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/instance_health.png"/>

- JVM明细信息
<img src="https://skywalkingtest.github.io/page-resources/3.2/instance_graph.png"/>

- 服务依赖树.
<img src="https://skywalkingtest.github.io/page-resources/3.2.1/service_dependency_tree.png"/>


# Test reports
- 自动化集成测试报告
  - [Java探针测试报告](https://github.com/SkywalkingTest/agent-integration-test-report)
- 性能测试报告
  - [Java探针测试报告](https://skywalkingtest.github.io/Agent-Benchmarks/)

# Contact Us
* 直接提交Issue
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ群: 392443393

# License
[Apache 2.0 License.](/LICENSE)
