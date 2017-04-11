Sky Walking Web UI
===============

<img src="src/main/resources/public/img/logo.png" alt="Sky Walking logo" height="90px" align="right" />

The web UI for [sky-walking APM](https://github.com/wu-sheng/sky-walking). 

[![Build Status](https://travis-ci.org/wu-sheng/sky-walking-ui.svg?branch=master)](https://travis-ci.org/wu-sheng/sky-walking-ui)

This independent UI repository works since sky-walking 3.0.

1. Topological graph of application cluster, inclusing tps of every application.
1. Alarms.
1. Trace query.

- Tested browsers

|Operation System|Browser|Version|
|:-----|:-----|:-----|
|Windows 7|Chrome|57.0.2987.133|
||IE 11|11.0.9600.18617|
||Firefox|50.1.0|
|Mac OS 10.12|Safari|10.1 (12603.1.30.0.34)|
||Chrome|57.0.2987.133|
||FireFox|52.0.2|

#### QuickStart
1. Requirements: at least Java 8
1. Download the skywalking-ui.tar as follows:
    > curl -O https://github.com/wu-sheng/sky-walking-ui/releases/download/v3.0-2017/skywalking-web.tar.gz     
    > tar -xvf skywalking-web.tar.gz  
    > cd skywalking-web/bin  
    > ./startup.sh
#### Configuring skywalking-ui
    * config/application.properties for configuring ui server port 
    * config/log4j2.xml for configuring ui server logging
    * config/collector_config.properties for configuring collector servers address