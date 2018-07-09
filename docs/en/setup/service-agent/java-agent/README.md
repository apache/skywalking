# Setup java agent
1. Find `agent` folder in SkyWalking 
2. Add `-javaagent:/path/to/skywalking-agent/skywalking-agent.jar` to VM argument. 

New agent package looks like this：
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
Add `-javaagent` argument to command line in which you start your app. And make sure to add it before the `-jar` argument. eg:
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
* [Filter traces through custom services](agent-optional-plugins/trace-ignore-plugin.md)

## Advanced Features
* [Override settings through System.properties](en/java-agent/Setting-override.md)
* [Direct uplink and disable naming discovery](en/java-agent/Direct-uplink.md)
* [Open TLS](en/java-agent/TLS.md)
* [Namespace Isolation](en/java-agent/Namespace.md)
* [Token Authentication](en/java-agent/Token-auth.md)
* Application Toolkit
    * [Overview](en/java-agent/Applicaton-toolkit.md)
    * [Use SkyWalking OpenTracing compatible tracer](en/java-agent/Opentracing.md)
    * Integration with log frameworks
      * [log4j](en/java-agent/Application-toolkit-log4j-1.x.md)
      * [log4j2](en/java-agent/Application-toolkit-log4j-2.x.md)
      * [logback](en/java-agent/Application-toolkit-logback-1.x.md)
    * [Trace by SkyWalking manual APIs](en/java-agent/Application-toolkit-trace.md)
    * [Trace across threads](en/java-agent/Application-toolkit-trace-cross-thread.md)

# Test
If you are interested in plugin compatible tests or agent performance, see the following reports.
* [Plugin Test](https://github.com/SkywalkingTest/agent-integration-test-report)
* [Java Agent Performance Test](https://skywalkingtest.github.io/Agent-Benchmarks/)