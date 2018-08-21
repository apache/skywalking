## 下载skywalking探针发布版本
- 前往 [发布页面下载](http://skywalking.apache.org/downloads/)

## 部署 skywalking javaagent
1. 拷贝agent目录到所需位置. 日志，插件和配置都包含在包中,请不要改变目录结构.
2. 增加JVM启动参数， -javaagent:/path/to/skywalking-agent/skywalking-agent.jar. 参数值为skywalking-agent.jar的绝对路径。 

新的 agent package 目录结构如下：
```
+-- skywalking-agent
    +-- activations
         apm-toolkit-log4j-1.x-activation.jar
         apm-toolkit-log4j-2.x-activation.jar
         apm-toolkit-logback-1.x-activation.jar
         ...
    +-- config
         agent.config  
    +-- plugins
         apm-dubbo-plugin.jar
         apm-feign-default-http-9.x.jar
         apm-httpClient-4.x-plugin.jar
         .....
    skywalking-agent.jar
```

- 启动被监控应用.

# 高级特性
- 插件全部放置在 `/plugins` 目录中.新的插件,也只需要在启动阶段,放在目录中,就自动生效,删除则失效.
- Log默认使用文件输出到 `/logs`目录中.

# 部署 java agent FAQs
- Linux Tomcat 7, Tomcat 8  
修改 `tomcat/bin/catalina.sh`,在首行加入如下信息.
```shell
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"; export CATALINA_OPTS
```

- Windows Tomcat 7, Tomcat 8  
修改 `tomcat/bin/catalina.bat`,在首行加入如下信息.
```shell
set "CATALINA_OPTS=-javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```
- JAR file  
在启动你的应用程序的命令行中添加 `-javaagent` 参数. 并确保在`-jar`参数之前添加它. 例如:
 ```shell
 java -javaagent:/path/to/skywalking-agent/skywalking-agent.jar -jar yourApp.jar
 ```