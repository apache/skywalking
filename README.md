Sky Walking
==========

<img src="http://wu-sheng.github.io/sky-walking/images/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

SkyWalking: Large-Scale Distributed Systems Tracing Infrastructure, also known Distributed Tracer。

[![Join the chat at https://gitter.im/sky-walking/Lobby](https://badges.gitter.im/sky-walking/Lobby.svg)](https://gitter.im/sky-walking/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/wu-sheng/sky-walking.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking)
![license](https://img.shields.io/aur/license/yaourt.svg)

# Abstract
* An open source Large-Scale Distributed Systems Tracing Infrastructure, also known a ditributed tracer.
* Based on [Google Dapper Paper: Dapper, a Large-Scale Distributed Systems Tracing Infrastructure](http://research.google.com/pubs/pub36356.html), [Simplified Chines](http://duanple.blog.163.com/blog/static/70971767201329113141336/)
* Supports popular rpc frameworks, such as [dubbo](https://github.com/alibaba/dubbo), [dubbox](https://github.com/dangdangdotcom/dubbox), [motan](https://github.com/weibocom/motan) etc., supports email-alert when application occurs unexpected exception。
* Execution time of creating span than 100μs.
* Easy to deploy, **even in product mode** ( since 2.x ) . No need of Hadoop, HBase, or Cassandra Cluster.

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

# 整体架构图
![2.0 架构图](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/2.0-2016/SkyWalkingArch.jpg)

# 典型页面展现 / Typical UI show
* 支持浏览器：Firefox/Chrome

## 实时调用链路
* 实时链路追踪展现
![追踪连路图1](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChain.png)
* 实时链路追踪详细信息查看
![追踪连路图2](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChainDetail.png)
* 实时链路追踪日志查看
![追踪连路图3](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/callChainLog.png)
* 实时链路异常告警邮件
![告警邮件](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/alarmMail.png)
* 添加应用
![添加应用](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/addApp.png)
* 应用列表展现
![应用列表展现](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/appList.png)

## 分析汇总
* 分析结果查询，根据viewpoint模糊匹配查询。查询方式为viewpoint: + 关键字
![应用列表展现](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/searchAnalysisResult.png)
* 分析结果展现，通过分析结果查询页面点击进入
![应用列表展现](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/analysisResult.png)
* 分析典型调用链展现
![应用列表展现](http://wu-sheng.github.io/sky-walking/sample-code/screenshoot/1.0b/typicalAnalysisResult.png)

# v2.0-2016 Quick Start

## Required of third party softwares
- JDK 1.8
- zookeeper 3.4.6
- mysql
- redis-3.0.5

## instrument javaagent jdk version
- support 1.6+ , instruments applications can run in jdk6

## Download and deploy servers
- Download Server release version. [Download](https://github.com/wu-sheng/sky-walking/releases)  (.tar.gz)

### run all servers in a Docker
- Docker version is good quick-test.

### deploy your own cluster


## Download agent
[ ![Download](https://api.bintray.com/packages/wu-sheng/skywalking/com.a.eye.skywalking-agent/images/download.svg) ](https://bintray.com/wu-sheng/skywalking/com.a.eye.skywalking-agent/_latestVersion)


## start application with -javaagent
```shell
java -jar ...  -javaagent:/..ospath../skywalking-agent-x.x.jar
```

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
	
