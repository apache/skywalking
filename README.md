Sky Walking
==========

<img src="docs/resources/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

SkyWalking: Large-Scale Distributed Systems Tracing Infrastructure, also known Distributed Tracer.

[![Build Status](https://travis-ci.org/wu-sheng/sky-walking.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking)
[![Coverage Status](https://coveralls.io/repos/github/wu-sheng/sky-walking/badge.svg?branch=master)](https://coveralls.io/github/wu-sheng/sky-walking?branch=master)
![license](https://img.shields.io/aur/license/yaourt.svg)
[![codebeat badge](https://codebeat.co/badges/579e4dce-1dc7-4f32-a163-c164eafa1335)](https://codebeat.co/projects/github-com-wu-sheng-sky-walking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/sky-walking/Lobby.svg)](https://gitter.im/sky-walking/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![OpenTracing-1.0 Badge](https://img.shields.io/badge/OpenTracing--1.0-enabled-blue.svg)](http://opentracing.io)
[![Release Version](https://img.shields.io/badge/stable-2.3--2017-brightgreen.svg)](https://github.com/wu-sheng/sky-walking/releases)

# News
* sky-walking v3.0 iteration begins... The top 2 important features are: [`Update the trace-structure`](https://github.com/wu-sheng/sky-walking/issues/83) and [`Analyze trace, and bring metric/analytic/cause up`](https://github.com/wu-sheng/sky-walking/issues/84)
* See feature codes at [branch feature/3.0](https://github.com/wu-sheng/sky-walking/tree/feature/3.0)
* The new UI release on [wu-sheng/sky-walking-ui](https://github.com/wu-sheng/sky-walking-ui)

# Abstract
* An open source Large-Scale Distributed Systems Tracing Infrastructure, also known a ditributed tracer.
* Based on [Google Dapper Paper: Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html), read [Simplified Chinese Version](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* Support popular rpc frameworks, such as [dubbo](https://github.com/alibaba/dubbo), [dubbox](https://github.com/dangdangdotcom/dubbox), [motan](https://github.com/weibocom/motan) etc., trigger email-alert when application occurs unexpected exception.
* Auto-instrumentation mechenism, **no need to CHANGE any application source code**.
* Easy to deploy, **even in product mode** (since 2.0) . No need of Hadoop, HBase, or Cassandra Cluster.
* Pure Java server implementation. provide gRPC (since 2.0)  and HTTP (since 2.1) cross-platform spans collecting service.


# Supported components

### web containers
* Tomcat 7
* Tomcat 8

### databases
* mysql
* oracle
* h2
* easily extend to support sybase, sqlserver, jtds, db2, informix

### rpc frameworks
* dubbo 
* dubbox 
* httpClient
* motan

### cache
* jedis

### opentracing supported frameworks
* motan
* hprose-java

# Contributors
* 吴晟 [wusheng](https://github.com/wu-sheng) &nbsp;&nbsp;wu.sheng@foxmail.com
* 张鑫 [zhangxin](https://github.com/ascrutae) &nbsp;&nbsp;


_Chinese Articles about sky-walking and distributed tracer_

<img src="docs/resources/toutiao.JPG" alt="Sky Walking TouTiao" height="280px" />

_If you are Chinese Developer, you can join QQ Group: 392443393, and **Tagged** Sky-Walking._

___

<a href="https://github.com/wu-sheng/sky-walking">
<img src="docs/resources/oneapm-award.png" alt="OneAPM Open Source Achievement Award" height="110px" align="left" />
</a>

In October 2016, Sky Walking won `OneAPM Open Source Achievement Award`. The award appreciates sky walking for its "*contribution to popularization of APM technology*". <br/>
[OneAPM](http://www.oneapm.com/) will provide financial support for the project in 2016-2017.<br/><br/> 
Thanks all users of sky walking project.

___


# Quick View
* distributed trace

![追踪连路图1](docs/resources/callChain.png?1=1)

![追踪连路图2](docs/resources/callChainDetail.png?1=1)

![追踪连路图3](docs/resources/callChainLog.png?1=1)

* alarm mail

![告警邮件](docs/resources/alarmMail.png?1=1)

# Document
* [WIKI](https://github.com/wu-sheng/sky-walking/wiki)
