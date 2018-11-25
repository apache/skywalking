# Setup java agent
1. Find `agent` folder in SkyWalking release package
1. Set `agent.service_name` in `config/agent.config`. Could be any String in English.
1. Set `collector.backend_service` in `config/agent.config`. Default point to `127.0.0.1:11800`, only works for local backend.
1. Add `-javaagent:/path/to/skywalking-package/agenxt/skywalking-agent.jar` to JVM argument. And make sure to add it before the `-jar` argument.

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
 
## Table of Agent Configuration Properties
This is the properties list supported in `agent/config/agent.config`.

property key | Description | Default |
----------- | ---------- | --------- | 
`agent.namespace` | Namespace isolates headers in cross process propagation. The HEADER name will be `HeaderName:Namespace`. | Not set | 
`agent.service_name` | Application(5.x)/Service(6.x) code is showed in sky-walking-ui. Suggestion: set a unique name for each service, service instance nodes share the same code | `Your_ApplicationName` |
`agent.sample_n_per_3_secs`|Negative or zero means off, by default.SAMPLE_N_PER_3_SECS means sampling N TraceSegment in 3 seconds tops.|Not set|
`agent.authentication`|Authentication active is based on backend setting, see application.yml for more details.For most scenarios, this needs backend extensions, only basic match auth provided in default implementation.|Not set|
`agent.span_limit_per_segment`|The max number of spans in a single segment. Through this config item, skywalking keep your application memory cost estimated.|Not set |
`agent.ignore_suffix`|If the operation name of the first span is included in this set, this segment should be ignored.|Not set|
`agent.is_open_debugging_class`|If true, skywalking agent will save all instrumented classes files in `/debugging` folder.Skywalking team may ask for these files in order to resolve compatible problem.|Not set|
`agent.active_v2_header`|Active V2 header in default.|`true`|
`agent.active_v1_header `|Deactive V1 header in default.|`false`|
`collector.grpc_channel_check_interval`|grpc channel status check interval.|`30`|
`collector.app_and_service_register_check_interval`|application and service registry check interval.|`3`|
`collector.backend_service`|Collector skywalking trace receiver service addresses.|`127.0.0.1:11800`|
`logging.level`|The log level. Default is debug.|`DEBUG`|
`logging.file_name`|Log file name.|`skywalking-api.log`|
`logging.dir`|Log files directory. Default is blank string, means, use "system.out" to output logs.|`""`|
`logging.max_file_size`|The max size of log file. If the size is bigger than this, archive the current file, and write into a new file.|`300 * 1024 * 1024`|
`jvm.buffer_size`|The buffer size of collected JVM info.|`60 * 10`|
`buffer.channel_size`|The buffer channel size.|`5`|
`buffer.buffer_size`|The buffer size.|`300`|
`dictionary.service_code_buffer_size`|The buffer size of application codes and peer|`10 * 10000`|
`dictionary.endpoint_name_buffer_size`|The buffer size of endpoint names and peer|`1000 * 10000`|
`plugin.mongodb.trace_param`|If true, trace all the parameters in MongoDB access, default is false. Only trace the operation, not include parameters.|`false`|
`plugin.elasticsearch.trace_dsl`|If true, trace all the DSL(Domain Specific Language) in ElasticSearch access, default is false.|`false`|
 
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
