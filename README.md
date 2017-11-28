Sky Walking Web UI
===============

<img src="src/main/resources/public/img/logo.png" alt="Sky Walking logo" height="90px" align="right" />

The web UI for [sky-walking APM](https://github.com/OpenSkywalking/skywalking). 

[![Build Status](https://travis-ci.org/OpenSkywalking/skywalking-ui.svg?branch=master)](https://travis-ci.org/OpenSkywalking/skywalking-ui)

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

#### QuickStart-zh
1. 需要JDK8+
2. 通过命令下载应用程序，或直接前往[发布页面](https://github.com/wu-sheng/sky-walking-ui/releases)下载

```shell
> curl -O https://github.com/wu-sheng/sky-walking-ui/releases/download/<project-version>/skywalking-web.tar.gz  
> tar -xvf skywalking-web.tar.gz  
> cd skywalking-web/bin  
> ./startup.sh
```

3. 配置应用程序
* 在`config/application.properties`文件中，设置webui的监听端口
* 在`config/collector_config.properties`文件中，设置collector集群或单实例地址，地址对应的是 `skywalking-collector` 项目中配置文件 `config/application.yml` 中配置项 `naming.jetty.host` 和 `naming.jetty.port`

4. 浏览器打开 `http://127.0.0.1:8080/`
