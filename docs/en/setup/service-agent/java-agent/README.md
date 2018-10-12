# Setup java agent
1. Find `agent` folder in SkyWalking release package
2. Set 

3. Add `-javaagent:/path/to/skywalking-package/agent/skywalking-agent.jar` to JVM argument. And make sure to add it before the `-jar` argument.

The agent release dist is included in Apache [official release](http://skywalking.apache.org/downloads/).MeshReceiverProvider New agent package looks like this.
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
    skywalking-agent.jar
```

- Start your application.

## Advanced features
- All plugins are in `/plugins` folder. The plugin jar is active when it is in there. Remove the plugin jar, it disabled.
- The default logging output folder is `/logs`.

## Install javaagent FAQs
- Linux Tomcat 7, Tomcat 8  
Change the first line of `tomcat/bin/catalina.sh`.
```shell
CATALINA_OPTS="$CATALINA_OPTS -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"; export CATALINA_OPTS
```

- Windows Tomcat 7, Tomcat 8  
Change the first line of `tomcat/bin/catalina.bat`.
```shell
set "CATALINA_OPTS=-javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```
- JAR file  
Add `-javaagent` argument to command line in which you start your app. eg:
 ```shell
 java -javaagent:/path/to/skywalking-agent/skywalking-agent.jar -jar yourApp.jar
 ```
## Supported middlewares, frameworks and libraries
See [supported list](Supported-list.md).

## Plugins
Java agent plugins are all pluggable. Optional plugins could be provided by source codes or in `optional-plugins` folder under agent.
For using these plugins, you need to compile source codes by yourself, or copy the certain plugins to `/plugins`.

Now, we have the following known plugins.
* [Trace Spring beans](agent-optional-plugins/Spring-bean-plugins.md)
* [Trace Oracle and Resin](agent-optional-plugins/Oracle-Resin-plugins.md)
* [Filter traces through specified endpoint name patterns](agent-optional-plugins/trace-ignore-plugin.md)

## Advanced Features
* Set the settings through system properties for config file override. Read [setting override](Setting-override.md).
* Use gRPC TLS to link backend. See [open TLS](TLS.md)
* Monitor a big cluster by different SkyWalking services. Use [Namespace](Namespace.md) to isolate the context propagation. 
* Set client [token](Token-auth.md) if backend open [token authentication](../../backend/backend-token-auth.md).
* Application Toolkit, are a collection of libraries, provided by skywalking APM. Using them, you have a bridge between your application and skywalking APM agent. 
    * If you want to use OpenTracing Java APIs, try [SkyWalking OpenTracing compatible tracer](Opentracing.md). More details you could find at http://opentracing.io
    * If you want to print trace context(e.g. traceId) in your logs, choose the log frameworks, [log4j](Application-toolkit-log4j-1.x.md), 
[log4j2](Application-toolkit-log4j-2.x.md), [logback](Application-toolkit-logback-1.x.md)
    * If you want to use annotations or SkyWalking native APIs to read context, try [SkyWalking manual APIs](Application-toolkit-trace.md)
    * If you want to continue traces across thread manually, use [across thread solution APIs](Application-toolkit-trace-cross-thread.md).
* If you want to specify the path of your agent.config file. Read [set config file through system properties](Specified-agent-config.md)

## Plugin Development Guide
SkyWalking java agent supports plugin to extend [the supported list](Supported-list.md). Please follow 
our [Plugin Development Guide](../../../guides/Java-Plugin-Development-Guide.md).

# Test
If you are interested in plugin compatible tests or agent performance, see the following reports.
* [Plugin Test](https://github.com/SkywalkingTest/agent-integration-test-report)
* [Java Agent Performance Test](https://skywalkingtest.github.io/Agent-Benchmarks/)