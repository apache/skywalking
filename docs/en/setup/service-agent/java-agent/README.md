# Setup java agent
1. Agent is available for JDK 8 - 14.
1. Find `agent` folder in SkyWalking release package
1. Set `agent.service_name` in `config/agent.config`. Could be any String in English.
1. Set `collector.backend_service` in `config/agent.config`. Default point to `127.0.0.1:11800`, only works for local backend.
1. Add `-javaagent:/path/to/skywalking-package/agent/skywalking-agent.jar` to JVM argument. And make sure to add it before the `-jar` argument.

The agent release dist is included in Apache [official release](http://skywalking.apache.org/downloads/). New agent package looks like this.
```
+-- agent
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
    +-- optional-plugins
         apm-gson-2.x-plugin.jar
         .....
    +-- bootstrap-plugins
         jdk-http-plugin.jar
         .....
    +-- logs
    skywalking-agent.jar
```

- Start your application.

## Install javaagent FAQs
- Linux Tomcat 7, Tomcat 8, Tomcat 9  
Change the first line of `tomcat/bin/catalina.sh`.
```shell
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"; export CATALINA_OPTS
```

- Windows Tomcat 7, Tomcat 8, Tomcat 9  
Change the first line of `tomcat/bin/catalina.bat`.
```shell
set "CATALINA_OPTS=-javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```

- JAR file  
Add `-javaagent` argument to command line in which you start your app. eg:
 ```shell
 java -javaagent:/path/to/skywalking-agent/skywalking-agent.jar -jar yourApp.jar
 ```

- Jetty  
Modify `jetty.sh`, add `-javaagent` argument to command line in which you start your app. eg:
```shell
export JAVA_OPTIONS="${JAVA_OPTIONS} -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```

# Plugins

## Supported middleware, framework and library
SkyWalking agent has supported various middlewares, frameworks and libraries.
Read [supported list](Supported-list.md) to get them and supported version.
If the plugin is in **Optional²** catalog, go to [optional plugins](#optional-plugins) section to learn how to active it.

- All plugins are in `/plugins` folder. The plugin jar is active when it is in there. Remove the plugin jar, it disabled.
- The default logging output folder is `/logs`.

## Optional Plugins
Java agent plugins are all pluggable. Optional plugins could be provided in `optional-plugins` folder under agent or 3rd party repositories.
For using these plugins, you need to put the target plugin jar file into `/plugins`.

Now, we have the following known optional plugins.
* [Plugin of tracing Spring annotation beans](agent-optional-plugins/Spring-annotation-plugin.md)
* [Plugin of tracing Oracle and Resin](agent-optional-plugins/Oracle-Resin-plugins.md)
* [Filter traces through specified endpoint name patterns](agent-optional-plugins/trace-ignore-plugin.md)
* Plugin of Gson serialization lib in optional plugin folder.
* Plugin of Zookeeper 3.4.x in optional plugin folder. The reason of being optional plugin is, many business irrelevant traces are generated, which cause extra payload to agents and backends. At the same time, those traces may be just heartbeat(s).
* [Customize enhance](Customize-enhance-trace.md) Trace methods based on description files, rather than write plugin or change source codes.
* Plugin of Spring Cloud Gateway 2.1.x in optional plugin folder. Please only active this plugin when you install agent in Spring Gateway. spring-cloud-gateway-2.x-plugin and spring-webflux-5.x-plugin are both required.
* Plugin of Spring Transaction in optional plugin folder. The reason of being optional plugin is, many local span are generated, which also spend more CPU, memory and network.
* [Plugin of Kotlin coroutine](agent-optional-plugins/Kotlin-Coroutine-plugin.md) provides the tracing across coroutines automatically. As it will add local spans to all across routines scenarios, Please assess the performance impact.
* Plugin of quartz-scheduler-2.x in the optional plugin folder. The reason for being an optional plugin is, many task scheduling systems are based on quartz-scheduler, this will cause duplicate tracing and link different sub-tasks as they share the same quartz level trigger, such as ElasticJob.
* Plugin of spring-webflux-5.x in the optional plugin folder. Please only activate this plugin when you use webflux alone as a web container. If you are using SpringMVC 5 or Spring Gateway, you don't need this plugin.
* Plugin of mybatis-3.x in optional plugin folder. The reason of being optional plugin is, many local span are generated, which also spend more CPU, memory and network.
* Plugin of sentinel-1.x in the optional plugin folder. The reason for being an optional plugin is, the sentinel plugin generates a large number of local spans, which have a potential performance impact.
* Plugin of ehcache-2.x in the optional plugin folder. The reason for being an optional plugin is, this plugin enhanced cache framework, generates large number of local spans, which have a potential performance impact.
* Plugin of guava-cache in the optional plugin folder. The reason for being an optional plugin is, this plugin enhanced cache framework, generates large number of local spans, which have a potential performance impact.

## Bootstrap class plugins
All bootstrap plugins are optional, due to unexpected risk. Bootstrap plugins are provided in `bootstrap-plugins` folder.
For using these plugins, you need to put the target plugin jar file into `/plugins`.

Now, we have the following known bootstrap plugins.
* Plugin of JDK HttpURLConnection. Agent is compatible with JDK 1.6+
* Plugin of JDK Callable and Runnable. Agent is compatible with JDK 1.6+


## Plugin Development Guide
SkyWalking java agent supports plugin to extend [the supported list](Supported-list.md). Please follow 
our [Plugin Development Guide](../../../guides/Java-Plugin-Development-Guide.md).
