# Setup java agent
1. Agent is available for JDK 8 - 12 in 7.x releases. JDK 1.6 - JDK 12 are supported in all 6.x releases [NOTICE¹](#notice)
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

## Supported middleware, framework and library
SkyWalking agent has supported various middlewares, frameworks and libraries.
Read [supported list](Supported-list.md) to get them and supported version.
If the plugin is in **Optional²** catalog, go to [optional plugins](#optional-plugins) section to learn how to active it.

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

- Jetty  
Modify `jetty.sh`, add `-javaagent` argument to command line in which you start your app. eg:
```shell
export JAVA_OPTIONS="${JAVA_OPTIONS} -javaagent:/path/to/skywalking-agent/skywalking-agent.jar"
```

## Table of Agent Configuration Properties
This is the properties list supported in `agent/config/agent.config`.

property key | Description | Default |
----------- | ---------- | --------- | 
`agent.namespace` | Namespace isolates headers in cross process propagation. The HEADER name will be `HeaderName:Namespace`. | Not set | 
`agent.service_name` | Application(5.x)/Service(6.x) code is showed in sky-walking-ui. Suggestion: set a unique name for each service, service instance nodes share the same code | `Your_ApplicationName` |
`agent.sample_n_per_3_secs`|Negative or zero means off, by default.SAMPLE_N_PER_3_SECS means sampling N TraceSegment in 3 seconds tops.|Not set|
`agent.authentication`|Authentication active is based on backend setting, see application.yml for more details.For most scenarios, this needs backend extensions, only basic match auth provided in default implementation.|Not set|
`agent.span_limit_per_segment`|The max number of spans in a single segment. Through this config item, SkyWalking keep your application memory cost estimated.|300 |
`agent.ignore_suffix`|If the operation name of the first span is included in this set, this segment should be ignored.|Not set|
`agent.is_open_debugging_class`|If true, skywalking agent will save all instrumented classes files in `/debugging` folder. SkyWalking team may ask for these files in order to resolve compatible problem.|Not set|
`agent.instance_uuid` |Instance uuid is the identity of an instance, SkyWalking treat same instance uuid as one instance.if empty, SkyWalking agent will generate an 32-bit uuid. Using `NAME:` as UUID prefix could set the customized instance name. Such as, set it as `NAME:SVR-INSTANCE-A`,  `SVR-INSTANCE-A` is the instance name. Otherwise, use `ServiceName`-pid:`id`@`hostname` as the instance name. |`""`|
`agent.instance_properties[key]=value` | Add service instance custom properties. | Not set|
`agent.cause_exception_depth`|How depth the agent goes, when log all cause exceptions.|`5`|
`agent.cool_down_threshold `|How long should the agent wait (in minute) before re-registering to the OAP server after receiving reset command.|`10`|
`agent.force_reconnection_period `|Force reconnection period of grpc, based on grpc_channel_check_interval.|`1`|
`agent.operation_name_threshold `|The operationName max length, setting this value > 500 is not recommended.|`500`|
`collector.grpc_channel_check_interval`|grpc channel status check interval.|`30`|
`collector.app_and_service_register_check_interval`|application and service registry check interval.|`3`|
`collector.backend_service`|Collector SkyWalking trace receiver service addresses.|`127.0.0.1:11800`|
`collector.grpc_upstream_timeout`|How long grpc client will timeout in sending data to upstream. Unit is second.|`30` seconds|
`collector.get_profile_task_interval`|Sniffer get profile task list interval.|`20`|
`logging.level`|The log level. Default is debug.|`DEBUG`|
`logging.file_name`|Log file name.|`skywalking-api.log`|
`logging.output`| Log output. Default is FILE. Use CONSOLE means output to stdout. |`FILE`|
`logging.dir`|Log files directory. Default is blank string, means, use "{theSkywalkingAgentJarDir}/logs  " to output logs. {theSkywalkingAgentJarDir} is the directory where the skywalking agent jar file is located |`""`|
`logging.pattern `|logging format. There are all conversion specifiers: <br>&nbsp;&nbsp;* `%level` means log level. <br>&nbsp;&nbsp;*  `%timestamp` means now of time with format `yyyy-MM-dd HH:mm:ss:SSS`.<br>&nbsp;&nbsp;*   `%thread` means name of current thread.<br>&nbsp;&nbsp;*   `%msg` means some message which user logged. <br>&nbsp;&nbsp;*  `%class` means SimpleName of TargetClass. <br>&nbsp;&nbsp;*  `%throwable` means a throwable which user called. <br>&nbsp;&nbsp;*  `%agent_name` means `agent.service_name`  |`%level %timestamp %thread %class : %msg %throwable`|
`logging.max_file_size`|The max size of log file. If the size is bigger than this, archive the current file, and write into a new file.|`300 * 1024 * 1024`|
`logging.max_history_files`|The max history log files. When rollover happened, if log files exceed this number,then the oldest file will be delete. Negative or zero means off, by default.|`-1`|
`jvm.buffer_size`|The buffer size of collected JVM info.|`60 * 10`|
`buffer.channel_size`|The buffer channel size.|`5`|
`buffer.buffer_size`|The buffer size.|`300`|
`dictionary.service_code_buffer_size`|The buffer size of application codes and peer|`10 * 10000`|
`dictionary.endpoint_name_buffer_size`|The buffer size of endpoint names and peer|`1000 * 10000`|
`profile.active`|If true, skywalking agent will enable profile when user create a new profile task. Otherwise disable profile.|`true`|
`plugin.peer_max_length `|Peer maximum description limit.|`200`|
`plugin.mongodb.trace_param`|If true, trace all the parameters in MongoDB access, default is false. Only trace the operation, not include parameters.|`false`|
`plugin.mongodb.filter_length_limit`|If set to positive number, the `WriteRequest.params` would be truncated to this length, otherwise it would be completely saved, which may cause performance problem.|`256`|
`plugin.elasticsearch.trace_dsl`|If true, trace all the DSL(Domain Specific Language) in ElasticSearch access, default is false.|`false`|
`plugin.springmvc.use_qualified_name_as_endpoint_name`|If true, the fully qualified method name will be used as the endpoint name instead of the request URL, default is false.|`false`|
`plugin.toolit.use_qualified_name_as_operation_name`|If true, the fully qualified method name will be used as the operation name instead of the given operation name, default is false.|`false`|
`plugin.mysql.trace_sql_parameters`|If set to true, the parameters of the sql (typically `java.sql.PreparedStatement`) would be collected.|`false`|
`plugin.mysql.sql_parameters_max_length`|If set to positive number, the `db.sql.parameters` would be truncated to this length, otherwise it would be completely saved, which may cause performance problem.|`512`|
`plugin.postgresql.trace_sql_parameters`|If set to true, the parameters of the sql (typically `java.sql.PreparedStatement`) would be collected.|`false`|
`plugin.postgresql.sql_parameters_max_length`|If set to positive number, the `db.sql.parameters` would be truncated to this length, otherwise it would be completely saved, which may cause performance problem.|`512`|
`plugin.solrj.trace_statement`|If true, trace all the query parameters(include deleteByIds and deleteByQuery) in Solr query request, default is false.|`false`|
`plugin.solrj.trace_ops_params`|If true, trace all the operation parameters in Solr request, default is false.|`false`|
`plugin.light4j.trace_handler_chain`|If true, trace all middleware/business handlers that are part of the Light4J handler chain for a request.|false|
`plugin.opgroup.*`|Support operation name customize group rules in different plugins. Read [Group rule supported plugins](op_name_group_rule.md)|Not set|
`plugin.springtransaction.simplify_transaction_definition_name`|If true, the transaction definition name will be simplified.|false|
`plugin.jdkthreading.threading_class_prefixes` | Threading classes (`java.lang.Runnable` and `java.util.concurrent.Callable`) and their subclasses, including anonymous inner classes whose name match any one of the `THREADING_CLASS_PREFIXES` (splitted by `,`) will be instrumented, make sure to only specify as narrow prefixes as what you're expecting to instrument, (`java.` and `javax.` will be ignored due to safety issues) | Not set |
`plugin.tomcat.collect_http_params`| This config item controls that whether the Tomcat plugin should collect the parameters of the request. | `false` |
`plugin.springmvc.collect_http_params`| This config item controls that whether the SpringMVC plugin should collect the parameters of the request, when your Spring application is based on Tomcat, consider only setting either `plugin.tomcat.collect_http_params` or `plugin.springmvc.collect_http_params`. | `false` |
`plugin.http.http_params_length_threshold`| When `COLLECT_HTTP_PARAMS` is enabled, how many characters to keep and send to the OAP backend, use negative values to keep and send the complete parameters, NB. this config item is added for the sake of performance.  | `1024` |

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

## Bootstrap class plugins
All bootstrap plugins are optional, due to unexpected risk. Bootstrap plugins are provided in `bootstrap-plugins` folder.
For using these plugins, you need to put the target plugin jar file into `/plugins`.

Now, we have the following known bootstrap plugins.
* Plugin of JDK HttpURLConnection. Agent is compatible with JDK 1.6+
* Plugin of JDK Callable and Runnable. Agent is compatible with JDK 1.6+

## Advanced Features
* Set the settings through system properties for config file override. Read [setting override](Setting-override.md).
* Use gRPC TLS to link backend. See [open TLS](TLS.md)
* Monitor a big cluster by different SkyWalking services. Use [Namespace](Namespace.md) to isolate the context propagation. 
* Set client [token](Token-auth.md) if backend open [token authentication](../../backend/backend-token-auth.md).
* Application Toolkit, are a collection of libraries, provided by SkyWalking APM. Using them, you have a bridge between your application and SkyWalking APM agent. 
    * If you want to use OpenTracing Java APIs, try [SkyWalking OpenTracing compatible tracer](Opentracing.md). More details you could find at http://opentracing.io
    * If you want to print trace context(e.g. traceId) in your logs, choose the log frameworks, [log4j](Application-toolkit-log4j-1.x.md), 
[log4j2](Application-toolkit-log4j-2.x.md), [logback](Application-toolkit-logback-1.x.md)
    * If you want to use annotations or SkyWalking native APIs to read context, try [SkyWalking manual APIs](Application-toolkit-trace.md)
    * If you want to continue traces across thread manually, use [across thread solution APIs](Application-toolkit-trace-cross-thread.md).
* If you want to specify the path of your agent.config file. Read [set config file through system properties](Specified-agent-config.md)

## Plugin Development Guide
SkyWalking java agent supports plugin to extend [the supported list](Supported-list.md). Please follow 
our [Plugin Development Guide](../../../guides/Java-Plugin-Development-Guide.md).

If some RPC framework endpoints(server side) could include parameter, please read [Operation Name Group Rule](op_name_group_rule.md),
and consider to add this feature.

# Test
If you are interested in plugin compatible tests or agent performance, see the following reports.
* [Plugin Test in every Pull Request](https://github.com/apache/skywalking/actions?query=workflow%3APluginsTest)
* [Java Agent Performance Test](https://skyapmtest.github.io/Agent-Benchmarks/)

# Notice
¹ Due to gRPC didn't support JDK 1.6 since 2018, SkyWalking abandoned the JDK 6/7 supports in all 7.x releases. 
But, with gRPC back forward compatibility(at least for now), all SkyWalking 6.x agents could work with 7.x, including the agent and backend.