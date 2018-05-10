Apache SkyWalking | [English](README.md)
==========

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: 针对分布式系统的APM（应用性能监控）系统，特别针对微服务、cloud native和容器化(Docker, Kubernetes, Mesos)架构

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)


[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm-incubating.svg)](http://skywalking.apache.org/downloads/)
[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/openskywalking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

# Abstract
**SkyWalking** 创建与2015年，提供分布式追踪功能。从5.x开始，项目进化为一个完成功能的[Application Performance Management](https://en.wikipedia.org/wiki/Application_performance_management)系统。
他被用于追踪、监控和诊断分布式系统，特别是使用微服务架构，云原生或容积技术。提供以下主要功能：
- 分布式追踪和上下文传输
- 应用、实例、服务性能指标分析
- 根源分析
- 应用拓扑分析
- 应用和服务依赖分析
- 慢服务检测
- 性能优化

# Core features
- 多语言探针或类库
  - Java自动探针，追踪和监控程序时，不需要修改源码。
  - 社区提供的其他多语言探针
    * [.NET Core](https://github.com/OpenSkywalking/skywalking-netcore) 
    * [Node.js](https://github.com/OpenSkywalking/skywalking-nodejs)
- 多种后端存储： ElasticSearch， H2
- 支持[OpenTracing](http://opentracing.io/)
  - Java自动探针支持和OpenTracing API协同工作
- 轻量级、完善功能的后端聚合和分析
- 现代化Web UI
- 日志集成
- 应用、实例和服务的告警

# Document
- [英文文档](docs/README.md)
- [中文文档](docs/README_ZH.md)

# 5.x Architecture
<img src="https://skywalkingtest.github.io/page-resources/5.0/architecture.png"/>

# code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior to wusheng@apache.org.


# Live Demo
- 北京服务器. [前往](http://49.4.12.44:8080/)
- 香港服务器. [前往](http://159.138.0.181:8080/)

# Screenshot
<img src="https://skywalkingtest.github.io/page-resources/5.0.0-beta/Dashboard.png"/>


- [查看所有系统截图](/docs/Screenshots.md)

# Test reports
- 自动化集成测试报告
  - [Java探针测试报告](https://github.com/SkywalkingTest/agent-integration-test-report)
- 性能测试报告
  - [Java探针测试报告](https://skywalkingtest.github.io/Agent-Benchmarks/)

# Users
<img src="https://skywalkingtest.github.io/page-resources/users/users-2018-04-18.png"/>

[报告新的用户案例](https://github.com/apache/incubator-skywalking/issues/443)

# Contact Us
* 直接提交Issue
- 订阅邮件列表: dev@skywalking.apache.org
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ群: 392443393

# License
[Apache 2.0 License.](/LICENSE)
