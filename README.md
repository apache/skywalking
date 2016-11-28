Sky Walking
==========

<img src="http://wu-sheng.github.io/sky-walking/images/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

SkyWalking: Large-Scale Distributed Systems Tracing Infrastructure, also known Distributed Tracer。

[![Build Status](https://travis-ci.org/wu-sheng/sky-walking.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking)
![license](https://img.shields.io/aur/license/yaourt.svg)
[![codebeat badge](https://codebeat.co/badges/579e4dce-1dc7-4f32-a163-c164eafa1335)](https://codebeat.co/projects/github-com-wu-sheng-sky-walking)
[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/sky-walking/Lobby.svg)](https://gitter.im/sky-walking/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Abstract
* An open source Large-Scale Distributed Systems Tracing Infrastructure, also known a ditributed tracer.
* Based on [Google Dapper Paper: Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html), [read Simplified Chinese Version](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* Support popular rpc frameworks, such as [dubbo](https://github.com/alibaba/dubbo), [dubbox](https://github.com/dangdangdotcom/dubbox), [motan](https://github.com/weibocom/motan) etc., trigger email-alert when application occurs unexpected exception。
* Execution time of creating span than 100μs.
* Easy to deploy, **even in product mode** (since 2.0) . No need of Hadoop, HBase, or Cassandra Cluster.
* Pure Java server implements. provide gRPC (since 2.0)  and HTTP (since 2.1) cross-platform spans collecting service.


# Supported components
* web container
  * Tomcat7
  * Tomcat8
* database
  * mysql
  * oracle
  * h2
  * easily extend to support sybase, sqlserver, jtds, db2, informix
* rpc framework
  * dubbo (tested 2.5.3)
  * dubbox (tested 2.8.4-dubbo protocol, 2.8.4-rest protocol)
  * httpClient 4.x
  * motan (tested 0.1.2)
* cache
  * jedix 2.x


# Contributors
* 吴晟 [wusheng](https://github.com/wu-sheng) &nbsp;&nbsp;wu.sheng@foxmail.com
* 张鑫 [zhangxin](https://github.com/ascrutae) &nbsp;&nbsp;


___

<a href="https://github.com/wu-sheng/sky-walking">
<img src="http://wu-sheng.github.io/sky-walking/sample-code/award/oneapm-award.png" alt="OneAPM Open Source Achievement Award" height="110px" align="left" />
</a>

In October 2016, Sky Walking won `OneAPM Open Source Achievement Award`. The award appreciates sky walking for its "*contribution to popularization of APM technology*". <br/>
[OneAPM](http://www.oneapm.com/) will provide financial support for the project in 2016-2017.<br/><br/> 
Thanks all users of sky walking project.
___

# Discussion
<img src="http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/chatapp/toutiao.JPG" alt="Sky Walking TouTiao" height="280px" align="left" />
* Mail to：wu.sheng@foxmail.com
* QQ Group：392443393
* If you are using SkyWalking，[Report to us](https://github.com/wu-sheng/sky-walking/issues/34) please.
* **Developer Org of sky-walking** is https://github.com/skywalking-developer
<br/>
<br/>
<br/>
<br/>
<br/>
<br/>
<br/>
<br/>

# Arch
![2.0 架构图](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/2.0-2016/SkyWalkingArch.jpg)

# Quick View
* distributed tracer
![追踪连路图1](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChain.png)

![追踪连路图2](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChainDetail.png)

![追踪连路图3](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChainLog.png)

* alarm mail
![告警邮件](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/alarmMail.png)


# v2.0-2016 Quick Start

## 1. Required of third party softwares
- JDK 1.6+ ( instruments applications can run in jdk6 )
- JDK 1.8 ( skywalking servers )
- zookeeper 3.4.6
- mysql
- redis-3.0.5

## 2. Download and deploy servers
- Download Server release version. [Download](https://github.com/wu-sheng/sky-walking/releases)  (.tar.gz)

### 2.1 Run all servers in a Docker
- Docker version include all-in-one servers.
- Suitable for quick-look or test env. High performance and Scalable are not your requirements.

### 2.2 Deploy your own cluster
- waiting for release.

## 3. Download agent
[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/com.a.eye.skywalking-agent/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/com.a.eye.skywalking-agent/_latestVersion)

## 4. Create users, applications and alarm rules in webui
- create user, remember your user id.
- create applications under the user, remember your application code.
- create alarm rules for each applications.

## 4. Start application with -javaagent
```shell
java -jar ...  -javaagent:/..ospath../skywalking-agent-x.x.jar -DuserId=x -DapplicationCode=y -Dservers=192.168.1.16:3300,192.168.1.17:3300
```

## 5. Aha, your applications are being instrumented

# Recent RoadMap
* [In the schedule] v2.1-2017
	* release a version following OpenTracing.io
	* provide bridge mode lib to integrate data of system and tracer
	* provide English doc as default. Also provide Chinese version on Gitbook or Wiki
	* support log framework(log4j, log4j2, logback) to output traceid in system log.
	
* [Dev] v2.0-2016
	* not dependency on Hadoop or HBase, easy to deploy or maintenance
	* support simple-cluster mode
	* support docker mode in single-server-instance
	* support more plugins
		* motan rpc framework (provided)
	
