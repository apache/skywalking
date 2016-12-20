Sky Walking
==========

<img src="http://wu-sheng.github.io/sky-walking/images/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

SkyWalking: Large-Scale Distributed Systems Tracing Infrastructure, also known Distributed Tracer。

[![Build Status](https://travis-ci.org/wu-sheng/sky-walking.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking)
![license](https://img.shields.io/aur/license/yaourt.svg)
[![codebeat badge](https://codebeat.co/badges/579e4dce-1dc7-4f32-a163-c164eafa1335)](https://codebeat.co/projects/github-com-wu-sheng-sky-walking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/sky-walking/Lobby.svg)](https://gitter.im/sky-walking/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![sky-walking-org](https://img.shields.io/badge/organization-sky--walking--ecosystem-brightgreen.svg)](https://github.com/skywalking-developer)

[![Release Version](https://img.shields.io/badge/sky--walking-2.0--release-brightgreen.svg)](https://github.com/wu-sheng/sky-walking/releases)
[![Dev Version](https://img.shields.io/badge/sky--walking-2.1--ondev-yellow.svg)](https://github.com/wu-sheng/sky-walking)

# Abstract
* An open source Large-Scale Distributed Systems Tracing Infrastructure, also known a ditributed tracer.
* Based on [Google Dapper Paper: Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html), [read Simplified Chinese Version](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* Support popular rpc frameworks, such as [dubbo](https://github.com/alibaba/dubbo), [dubbox](https://github.com/dangdangdotcom/dubbox), [motan](https://github.com/weibocom/motan) etc., trigger email-alert when application occurs unexpected exception。
* Easy to deploy, **even in product mode** (since 2.0) . No need of Hadoop, HBase, or Cassandra Cluster.
* Pure Java server implements. provide gRPC (since 2.0)  and HTTP (since 2.1) cross-platform spans collecting service.


# Supported components
### web container
* Tomcat 7
* Tomcat 8

### database
* mysql
* oracle
* h2
* easily extend to support sybase, sqlserver, jtds, db2, informix


### rpc framework
* dubbo 
* dubbox 
* httpClient
* motan

### cache
* jedis

# Contributors
* 吴晟 [wusheng](https://github.com/wu-sheng) &nbsp;&nbsp;wu.sheng@foxmail.com
* 张鑫 [zhangxin](https://github.com/ascrutae) &nbsp;&nbsp;


_Chinese Articles about sky-walking and distributed tracer_

<img src="http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/chatapp/toutiao.JPG" alt="Sky Walking TouTiao" height="280px" />

_If you are Chinese Developer, you can join QQ Group: 392443393, and **Tagged** Sky-Walking._
___

<a href="https://github.com/wu-sheng/sky-walking">
<img src="http://wu-sheng.github.io/sky-walking/sample-code/award/oneapm-award.png" alt="OneAPM Open Source Achievement Award" height="110px" align="left" />
</a>

In October 2016, Sky Walking won `OneAPM Open Source Achievement Award`. The award appreciates sky walking for its "*contribution to popularization of APM technology*". <br/>
[OneAPM](http://www.oneapm.com/) will provide financial support for the project in 2016-2017.<br/><br/> 
Thanks all users of sky walking project.
___


# Quick View
* distributed tracer
![追踪连路图1](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChain.png)

![追踪连路图2](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChainDetail.png)

![追踪连路图3](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChainLog.png)

* alarm mail
![告警邮件](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/alarmMail.png)

# Document
* [WIKI](https://github.com/wu-sheng/sky-walking/wiki)
