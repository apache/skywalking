Apache SkyWalking
==========

<img src="https://skywalkingtest.github.io/page-resources/logo/sw-big-dark-1200.png" alt="Sky Walking logo" height="90px" align="right" />

**SkyWalking**: 针对分布式系统的APM（应用性能监控）系统，特别针对微服务、cloud native和容器化(Docker, Kubernetes, Mesos)架构

[![GitHub stars](https://img.shields.io/github/stars/apache/incubator-skywalking.svg?style=for-the-badge&label=Stars&logo=github)](https://github.com/apache/incubator-skywalking)
[![Twitter Follow](https://img.shields.io/twitter/follow/asfskywalking.svg?style=for-the-badge&label=Follow&logo=twitter)](https://twitter.com/AsfSkyWalking)


[![Maven Central](https://img.shields.io/maven-central/v/org.apache.skywalking/apache-skywalking-apm-incubating.svg)](http://skywalking.apache.org/downloads/)
[![Build Status](https://travis-ci.org/apache/incubator-skywalking.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/openskywalking/Lobby.svg)](https://gitter.im/openskywalking/Lobby)
[![OpenTracing-1.x Badge](https://img.shields.io/badge/OpenTracing--1.x-enabled-blue.svg)](http://opentracing.io)

# Abstract
**SkyWalking** 是一个开源的APM系统，其中用于监控，链路，诊断分布式系统，特别是使用微服务架构，云原生或容积技术

核心功能如下：

- 应用、实例、服务性能指标分析
- 根源分析
- 应用拓扑分析
- 应用、实例、服务性能依赖分析
- 慢服务检测
- 性能优化
- 分布式追踪和上下文传输
- 报警


<img src="https://skywalkingtest.github.io/page-resources/6-beta-overview.png"/>

SkyWalking支持收集不同格式的，不同来源的链路数据和指标数据
其中包括：
1. SkyWalking 提供了Java, .NET Core and NodeJS的自动探针
1. 接收服务网格Istio遥测的数据
1. 接收`Zipkin`v1和v2版本格式的数据


# 文档
- [6.x Documents](docs/README_ZH.md). 

```
Skywalking 5.x 仍然被社区支持, 而且Agent与后端的通信协议也与6.x兼容。
你可以切换至5.x分支，找到关于5.x的详细信息
```

- 跳转 [5.x pages](https://github.com/apache/incubator-skywalking/tree/5.x)。5.x 的文档 [here](https://github.com/apache/incubator-skywalking/blob/5.x/docs/README.md).


# 下载
请前往[releases page](http://skywalking.apache.org/downloads/)下载`Apache SkyWalking`的发布版.


# Code of conduct
This project adheres to the Contributor Covenant [code of conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. 
Please report unacceptable behavior to dev@skywalking.apache.org .

# Live Demo
- 北京服务器 [goto](http://106.75.237.45:8080/)
  - 用户名: admin
  - 密码: admin

# 截图
<table>
  <tr>
    <td width="50%" align="center"><b>Under javaagent observing</b></td>
    <td width="50%" align="center"><b>Observe on Istio</b></td>
  </tr>
  <tr>
    <td><img src="https://skywalkingtest.github.io/page-resources/6.0.0-alpha/Topology.png"/>
</td>
    <td><img src="https://skywalkingtest.github.io/page-resources/6.0.0-alpha/Istio/Topology.png"/>
</td>
   <tr>
     <td align="center"><a href="docs/Screenshots.md#agent">More screenshots</a></td>
     <td align="center"><a href="docs/Screenshots.md#istio">More screenshots</a></td>
  </tr>
</table>

# 如何编译项目
详细信息请查看 [document](docs/en/guides/How-to-build.md).

# 联系我们
* 提交一个 [issue](https://github.com/apache/incubator-skywalking/issues)
* 订阅邮件列表: **dev@skywalking.apache.org**. （发送邮件给`dev-subscribe@skywalking.apache.org`，就可以订阅邮件列表了.
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ群: 392443393

# 谁在使用Skywalking?
大量的公司和组织在使用Skywalking作为研究，生产或者商业产品，下面就是SkyWalking的**用户墙**

<img src="https://skywalkingtest.github.io/page-resources/users/users-2018-11-30.png"/>

我们鼓励Skywalking的使用者将自己增加至用户墙上 [PoweredBy](docs/powered-by.md)

# 生态系统

<p align="center">
<br/><br/>
<img src="https://landscape.cncf.io/images/cncf-landscape.svg" width="150"/>&nbsp;&nbsp;<img src="https://landscape.cncf.io/images/cncf.svg" width="200"/>
<br/><br/>
SkyWalking使得<a href="https://landscape.cncf.io/landscape=observability-and-analysis&license=apache-license-2-0">CNCF云原生生态系统更为充实。

</p>

<p align="center">
<a href="https://openapm.io"><img src="https://openapm.io/static/media/openapm_logo.svg" width="100"/></a> 
  <br/>Skywalking使得<a href="https://openapm.io">OpenAPM生态系统更为充实！</a>
</p>

# GitHub Star 增长图
[![Stargazers over time](https://starcharts.herokuapp.com/apache/incubator-skywalking.svg)](https://starcharts.herokuapp.com/apache/incubator-skywalking)

# 协议
[Apache 2.0 License.](/LICENSE)
