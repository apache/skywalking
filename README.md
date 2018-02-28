English | [简体中文](./README.zh-CN.md)

Apache SkyWalking Web UI
===============

<img src="https://skywalkingtest.github.io/page-resources/3.0/skywalking.png" alt="Sky Walking logo" height="90px" align="right" />

The web UI for [SkyWalking APM](https://github.com/apache/incubator-skywalking).

[![Build Status](https://travis-ci.org/apache/incubator-skywalking-ui.svg?branch=master)](https://travis-ci.org/apache/incubator-skywalking-ui)

SkyWalking Web UI 5.0 is developing, **NOW!!**. Submit issue and [@hanahmily](https://github.com/hanahmily) if you want to proposal something. 

___
_SkyWalking 3.0 documents:_

This independent UI repository works since sky-walking 3.0.

1. Realtime Application Topological Graph
1. Distributed Trace Query.
1. Application Instances Overview.
1. JVM Metric View of application instances.
1. The relationship of services.

Recommand browsers: Safari & Chrome

#### QuickStart
1. Requirement: Java 8+
2. Download and startup

```shell
> curl -O https://github.com/wu-sheng/sky-walking-ui/releases/download/<project-version>/skywalking-web.tar.gz
> tar -xvf skywalking-web.tar.gz
> cd skywalking-web/bin
> ./startup.sh
```
3. Configuring skywalking-ui
* Set server listening port in `config/application.properties`
* Set log in `config/log4j2.xml`
* Set addresses of collector servers in `config/collector_config.properties`

4. open `http://127.0.0.1:8080/`
