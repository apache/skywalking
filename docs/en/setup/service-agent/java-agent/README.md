# Setup java agent
1. Agent is available for JDK 8 - 14 in 7.x releases. JDK 1.6 - JDK 12 are supported in all 6.x releases [NOTICE¹](#notice)
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

## Table of Agent Configuration Properties
This is the properties list supported in `agent/config/agent.config`.

property key | Description | Default |
----------- | ---------- | --------- | 
`agent.namespace` | Namespace isolates headers in cross process propagation. The HEADER name will be `HeaderName:Namespace`. | Not set | 
`agent.service_name` | The service name to represent a logic group providing the same capabilities/logic. Suggestion: set a unique name for every logic service group, service instance nodes share the same code, Max length is 50(UTF-8 char). Optional, once `service_name` follows `<group name>::<logic name>` format, OAP server assigns the group name to the service metadata.| `Your_ApplicationName` |
`agent.sample_n_per_3_secs`|Negative or zero means off, by default.SAMPLE_N_PER_3_SECS means sampling N TraceSegment in 3 seconds tops.|Not set|
`agent.authentication`|Authentication active is based on backend setting, see application.yml for more details.For most scenarios, this needs backend extensions, only basic match auth provided in default implementation.|Not set|
`agent.span_limit_per_segment`|The max number of spans in a single segment. Through this config item, SkyWalking keep your application memory cost estimated.|300 |
`agent.ignore_suffix`|If the operation name of the first span is included in this set, this segment should be ignored.|Not set|
`agent.is_open_debugging_class`|If true, skywalking agent will save all instrumented classes files in `/debugging` folder. SkyWalking team may ask for these files in order to resolve compatible problem.|Not set|
`agent.is_cache_enhanced_class`|If true, SkyWalking agent will cache all instrumented classes files to memory or disk files (decided by class cache mode), allow another java agent to enhance those classes that enhanced by SkyWalking agent. To use some Java diagnostic tools (such as BTrace, Arthas) to diagnose applications or add a custom java agent to enhance classes, you need to enable this feature. [Read this FAQ for more details](../../../FAQ/Compatible-with-other-javaagent-bytecode-processing.md) |`false`|
`agent.class_cache_mode`|The instrumented classes cache mode: `MEMORY` or `FILE`. `MEMORY`: cache class bytes to memory, if instrumented classes is too many or too large, it may take up more memory. `FILE`: cache class bytes in `/class-cache` folder, automatically clean up cached class files when the application exits.|`MEMORY`|
`agent.instance_name` |Instance name is the identity of an instance, should be unique in the service. If empty, SkyWalking agent will generate an 32-bit uuid. Default, use `UUID`@`hostname` as the instance name. Max length is 50(UTF-8 char)|`""`|
`agent.instance_properties[key]=value` | Add service instance custom properties. | Not set|
`agent.cause_exception_depth`|How depth the agent goes, when log all cause exceptions.|`5`|
`agent.force_reconnection_period `|Force reconnection period of grpc, based on grpc_channel_check_interval.|`1`|
`agent.operation_name_threshold `|The operationName max length, setting this value > 190 is not recommended.|`150`|
`agent.keep_tracing`|Keep tracing even the backend is not available if this value is `true`.|`false`|
`agent.force_tls`|Force open TLS for gRPC channel if this value is `true`.|`false`|
`osinfo.ipv4_list_size`| Limit the length of the ipv4 list size. |`10`|
`collector.grpc_channel_check_interval`|grpc channel status check interval.|`30`|
`collector.heartbeat_period`|agent heartbeat report period. Unit, second.|`30`|
`collector.properties_report_period_factor`|The agent sends the instance properties to the backend every `collector.heartbeat_period * collector.properties_report_period_factor` seconds |`10`|
`collector.backend_service`|Collector SkyWalking trace receiver service addresses.|`127.0.0.1:11800`|
`collector.grpc_upstream_timeout`|How long grpc client will timeout in sending data to upstream. Unit is second.|`30` seconds|
`collector.get_profile_task_interval`|Sniffer get profile task list interval.|`20`|
`collector.get_agent_dynamic_config_interval`|Sniffer get agent dynamic config interval|`20`|
`collector.dns_period_resolve_active`|If true, skywalking agent will enable periodically resolving DNS to update receiver service addresses.|`false`|
`logging.level`|Log level: TRACE, DEBUG, INFO, WARN, ERROR, OFF. Default is info.|`INFO`|
`logging.file_name`|Log file name.|`skywalking-api.log`|
`logging.output`| Log output. Default is FILE. Use CONSOLE means output to stdout. |`FILE`|
`logging.dir`|Log files directory. Default is blank string, means, use "{theSkywalkingAgentJarDir}/logs  " to output logs. {theSkywalkingAgentJarDir} is the directory where the skywalking agent jar file is located |`""`|
`logging.resolver`|Logger resolver: `PATTERN` or `JSON`. The default is `PATTERN`, which uses `logging.pattern` to print traditional text logs. `JSON` resolver prints logs in JSON format. |`PATTERN`|
`logging.pattern `|Logging format. There are all conversion specifiers: <br>&nbsp;&nbsp;* `%level` means log level. <br>&nbsp;&nbsp;*  `%timestamp` means now of time with format `yyyy-MM-dd HH:mm:ss:SSS`.<br>&nbsp;&nbsp;*   `%thread` means name of current thread.<br>&nbsp;&nbsp;*   `%msg` means some message which user logged. <br>&nbsp;&nbsp;*  `%class` means SimpleName of TargetClass. <br>&nbsp;&nbsp;*  `%throwable` means a throwable which user called. <br>&nbsp;&nbsp;*  `%agent_name` means `agent.service_name`. Only apply to the `PatternLogger`. |`%level %timestamp %thread %class : %msg %throwable`|
`logging.max_file_size`|The max size of log file. If the size is bigger than this, archive the current file, and write into a new file.|`300 * 1024 * 1024`|
`logging.max_history_files`|The max history log files. When rollover happened, if log files exceed this number,then the oldest file will be delete. Negative or zero means off, by default.|`-1`|
`statuscheck.ignored_exceptions`|Listed exceptions would not be treated as an error. Because in some codes, the exception is being used as a way of controlling business flow.|`""`|
`statuscheck.max_recursive_depth`|The max recursive depth when checking the exception traced by the agent. Typically, we don't recommend setting this more than 10, which could cause a performance issue. Negative value and 0 would be ignored, which means all exceptions would make the span tagged in error status.|`1`|
`correlation.element_max_number`|Max element count in the correlation context.|3|
`correlation.value_max_length`|Max value length of each element.|`128`|
`correlation.auto_tag_keys`|Tag the span by the key/value in the correlation context, when the keys listed here exist.|`""`|
`jvm.buffer_size`|The buffer size of collected JVM info.|`60 * 10`|
`buffer.channel_size`|The buffer channel size.|`5`|
`buffer.buffer_size`|The buffer size.|`300`|
`profile.active`|If true, skywalking agent will enable profile when user create a new profile task. Otherwise disable profile.|`true`|
`profile.max_parallel`|Parallel monitor segment count|`5`|
`profile.duration`|Max monitor segment time(minutes), if current segment monitor time out of limit, then stop it.|`10`|
`profile.dump_max_stack_depth`|Max dump thread stack depth|`500`|
`profile.snapshot_transport_buffer_size`|Snapshot transport to backend buffer size|`50`|
`meter.active`|If true, the agent collects and reports metrics to the backend.|`true`|
`meter.report_interval`|Report meters interval. The unit is second|`20`|
`meter.max_meter_size`| Max size of the meter pool |`500`|
`plugin.mount` | Mount the specific folders of the plugins. Plugins in mounted folders would work. | `plugins,activations` |
`plugin.peer_max_length `|Peer maximum description limit.|`200`|
`plugin.exclude_plugins `|Exclude some plugins define in plugins dir.Plugin names is defined in [Agent plugin list](Plugin-list.md)|`""`|
`plugin.mongodb.trace_param`|If true, trace all the parameters in MongoDB access, default is false. Only trace the operation, not include parameters.|`false`|
`plugin.mongodb.filter_length_limit`|If set to positive number, the `WriteRequest.params` would be truncated to this length, otherwise it would be completely saved, which may cause performance problem.|`256`|
`plugin.elasticsearch.trace_dsl`|If true, trace all the DSL(Domain Specific Language) in ElasticSearch access, default is false.|`false`|
`plugin.springmvc.use_qualified_name_as_endpoint_name`|If true, the fully qualified method name will be used as the endpoint name instead of the request URL, default is false.|`false`|
`plugin.toolit.use_qualified_name_as_operation_name`|If true, the fully qualified method name will be used as the operation name instead of the given operation name, default is false.|`false`|
`plugin.jdbc.trace_sql_parameters`|If set to true, the parameters of the sql (typically `java.sql.PreparedStatement`) would be collected.|`false`|
`plugin.jdbc.sql_parameters_max_length`|If set to positive number, the `db.sql.parameters` would be truncated to this length, otherwise it would be completely saved, which may cause performance problem.|`512`|
`plugin.jdbc.sql_body_max_length`|If set to positive number, the `db.statement` would be truncated to this length, otherwise it would be completely saved, which may cause performance problem.|`2048`|
`plugin.solrj.trace_statement`|If true, trace all the query parameters(include deleteByIds and deleteByQuery) in Solr query request, default is false.|`false`|
`plugin.solrj.trace_ops_params`|If true, trace all the operation parameters in Solr request, default is false.|`false`|
`plugin.light4j.trace_handler_chain`|If true, trace all middleware/business handlers that are part of the Light4J handler chain for a request.|false|
`plugin.opgroup.*`|Support operation name customize group rules in different plugins. Read [Group rule supported plugins](../../backend/endpoint-grouping-rules.md)|Not set|
`plugin.springtransaction.simplify_transaction_definition_name`|If true, the transaction definition name will be simplified.|false|
`plugin.jdkthreading.threading_class_prefixes` | Threading classes (`java.lang.Runnable` and `java.util.concurrent.Callable`) and their subclasses, including anonymous inner classes whose name match any one of the `THREADING_CLASS_PREFIXES` (splitted by `,`) will be instrumented, make sure to only specify as narrow prefixes as what you're expecting to instrument, (`java.` and `javax.` will be ignored due to safety issues) | Not set |
`plugin.tomcat.collect_http_params`| This config item controls that whether the Tomcat plugin should collect the parameters of the request. Also, activate implicitly in the profiled trace. | `false` |
`plugin.springmvc.collect_http_params`| This config item controls that whether the SpringMVC plugin should collect the parameters of the request, when your Spring application is based on Tomcat, consider only setting either `plugin.tomcat.collect_http_params` or `plugin.springmvc.collect_http_params`. Also, activate implicitly in the profiled trace. | `false` |
`plugin.httpclient.collect_http_params`| This config item controls that whether the HttpClient plugin should collect the parameters of the request | `false` |
`plugin.http.http_params_length_threshold`| When `COLLECT_HTTP_PARAMS` is enabled, how many characters to keep and send to the OAP backend, use negative values to keep and send the complete parameters, NB. this config item is added for the sake of performance.  | `1024` |
`plugin.http.http_headers_length_threshold`| When `include_http_headers` declares header names, this threshold controls the length limitation of all header values. use negative values to keep and send the complete headers. Note. this config item is added for the sake of performance. | `2048` |
`plugin.http.include_http_headers`| Set the header names, which should be collected by the plugin. Header name must follow `javax.servlet.http` definition. Multiple names should be split by comma.  | ``(No header would be collected) |
`plugin.feign.collect_request_body`| This config item controls that whether the Feign plugin should collect the http body of the request. | `false` |
`plugin.feign.filter_length_limit`| When `COLLECT_REQUEST_BODY` is enabled, how many characters to keep and send to the OAP backend, use negative values to keep and send the complete body.  | `1024` |
`plugin.feign.supported_content_types_prefix`| When `COLLECT_REQUEST_BODY` is enabled and content-type start with SUPPORTED_CONTENT_TYPES_PREFIX, collect the body of the request , multiple paths should be separated by `,`  | `application/json,text/` |
`plugin.influxdb.trace_influxql`|If true, trace all the influxql(query and write) in InfluxDB access, default is true.|`true`|
`plugin.dubbo.collect_consumer_arguments`| Apache Dubbo consumer collect `arguments` in RPC call, use `Object#toString` to collect `arguments`. |`false`| 
`plugin.dubbo.consumer_arguments_length_threshold`| When `plugin.dubbo.collect_consumer_arguments` is `true`, Arguments of length from the front will to the OAP backend |`256`| 
`plugin.dubbo.collect_provider_arguments`| Apache Dubbo provider collect `arguments` in RPC call, use `Object#toString` to collect `arguments`. |`false`| 
`plugin.dubbo.consumer_provider_length_threshold`| When `plugin.dubbo.provider_consumer_arguments` is `true`, Arguments of length from the front will to the OAP backend |`256`| 
`plugin.kafka.bootstrap_servers`| A list of host/port pairs to use for establishing the initial connection to the Kafka cluster. | `localhost:9092`|
`plugin.kafka.get_topic_timeout`| Timeout period of reading topics from the Kafka server, the unit is second. |`10`|
`plugin.kafka.consumer_config`| Kafka producer configuration. ||
`plugin.kafka.producer_config`| Kafka producer configuration. Read [producer configure](http://kafka.apache.org/24/documentation.html#producerconfigs) to get more details. Check [Kafka report doc](How-to-enable-kafka-reporter.md) for more details and examples. | |
`plugin.kafka.topic_meter` | Specify which Kafka topic name for Meter System data to report to. | `skywalking_meters` |
`plugin.kafka.topic_metrics` | Specify which Kafka topic name for JVM metrics data to report to. | `skywalking_metrics` |
`plugin.kafka.topic_segment` | Specify which Kafka topic name for traces data to report to. | `skywalking_segments` |
`plugin.kafka.topic_profilings` | Specify which Kafka topic name for Thread Profiling snapshot to report to. | `skywalking_profilings` |
`plugin.kafka.topic_management` | Specify which Kafka topic name for the register or heartbeat data of Service Instance to report to. | `skywalking_managements` |
`plugin.springannotation.classname_match_regex` |  Match spring beans with regular expression for the class name. Multiple expressions could be separated by a comma. This only works when `Spring annotation plugin` has been activated. | `All the spring beans tagged with @Bean,@Service,@Dao, or @Repository.` |
`plugin.toolkit.log.transmit_formatted` | Whether or not to transmit logged data as formatted or un-formatted. | `true` |
`plugin.toolkit.log.grpc.reporter.server_host` | Specify which grpc server's host for log data to report to. | `127.0.0.1` |
`plugin.toolkit.log.grpc.reporter.server_port` | Specify which grpc server's port for log data to report to. | `11800` |
`plugin.toolkit.log.grpc.reporter.max_message_size` | Specify the maximum size of log data for grpc client to report to. | `10485760` |
`plugin.toolkit.log.grpc.reporter.upstream_timeout` | How long grpc client will timeout in sending data to upstream. Unit is second.|`30` seconds|

## Dynamic Configurations
All configurations above are static, if you need to change some agent settings at runtime, please read [CDS - Configuration Discovery Service document](configuration-discovery.md) for more details.

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

## Bootstrap class plugins
All bootstrap plugins are optional, due to unexpected risk. Bootstrap plugins are provided in `bootstrap-plugins` folder.
For using these plugins, you need to put the target plugin jar file into `/plugins`.

Now, we have the following known bootstrap plugins.
* Plugin of JDK HttpURLConnection. Agent is compatible with JDK 1.6+
* Plugin of JDK Callable and Runnable. Agent is compatible with JDK 1.6+

## The Logic Endpoint
In default, all the RPC server-side names as entry spans, such as RESTFul API path and gRPC service name, would be endpoints with metrics.
At the same time, SkyWalking introduces the logic endpoint concept, which allows plugins and users to add new endpoints without adding new spans.
The following logic endpoints are added automatically by plugins.
1. GraphQL Query and Mutation are logic endpoints by using the names of them.
1. Spring's ScheduledMethodRunnable jobs are logic endpoints. The name format is `SpringScheduled`/`${className}`/`${methodName}`.
1. Apache ShardingSphere ElasticJob's jobs are logic endpoints. The name format is `ElasticJob`/`${jobName}`.
1. XXLJob's jobs are logic endpoints. The name formats include `xxl-job`/`MethodJob`/`${className}`.`${methodName}`, `xxl-job`/`ScriptJob`/`${GlueType}`/`id`/`${jobId}`, and `xxl-job`/`SimpleJob`/`${className}`.
1. Quartz(optional plugin)'s jobs are logic endpoints. the name format is `quartz-scheduler`/`${className}`.

User could use the SkyWalking's application toolkits to add the tag into the local span to label the span as a logic endpoint in the analysis stage.
The tag is, key=`x-le` and value = `{"logic-span":true}`.

## Advanced Features
* Set the settings through system properties for config file override. Read [setting override](Setting-override.md).
* Use gRPC TLS to link backend. See [open TLS](TLS.md)
* Monitor a big cluster by different SkyWalking services. Use [Namespace](Namespace.md) to isolate the context propagation. 
* Set client [token](Token-auth.md) if backend open [token authentication](../../backend/backend-token-auth.md).
* Application Toolkit, are a collection of libraries, provided by SkyWalking APM. Using them, you have a bridge between your application and SkyWalking APM agent. 
    * If you want your codes to interact with SkyWalking agent, including `getting trace id`, `setting tags`, `propagating custom data` etc.. Try [SkyWalking manual APIs](Application-toolkit-trace.md).
    * If you require customized metrics, try [SkyWalking Meter System Toolkit](Application-toolkit-meter.md).
    * If you want to print trace context(e.g. traceId) in your logs, or collect logs, choose the log frameworks, [log4j](Application-toolkit-log4j-1.x.md), 
[log4j2](Application-toolkit-log4j-2.x.md), [logback](Application-toolkit-logback-1.x.md)
    * If you want to continue traces across thread manually, use [across thread solution APIs](Application-toolkit-trace-cross-thread.md).
    * If you want to forward MicroMeter/Spring Sleuth metrics to Meter System, use [SkyWalking MicroMeter Register](Application-toolkit-micrometer.md).
    * If you want to use OpenTracing Java APIs, try [SkyWalking OpenTracing compatible tracer](Opentracing.md). More details you could find at http://opentracing.io
    * If you want to tolerate some exceptions, read [tolerate custom exception doc](How-to-tolerate-exceptions.md).
* If you want to specify the path of your agent.config file. Read [set config file through system properties](Specified-agent-config.md)

## Advanced Reporters
The advanced report provides an alternative way to submit the agent collected data to the backend. All of them are in the `optional-reporter-plugins` folder, move the one you needed into the `reporter-plugins` folder for the activation. **Notice, don't try to activate multiple reporters, that could cause unexpected fatal errors.**

* Use Kafka to transport the traces, JVM metrics, instance properties, and profiled snapshots to the backend. Read the [How to enable Kafka Reporter](How-to-enable-kafka-reporter.md) for more details.

## Plugin Development Guide
SkyWalking java agent supports plugin to extend [the supported list](Supported-list.md). Please follow 
our [Plugin Development Guide](../../../guides/Java-Plugin-Development-Guide.md).

# Test
If you are interested in plugin compatible tests or agent performance, see the following reports.
* [Plugin Test in every Pull Request](https://github.com/apache/skywalking/actions?query=workflow%3APluginsTest)
* [Java Agent Performance Test](https://skyapmtest.github.io/Agent-Benchmarks/)
