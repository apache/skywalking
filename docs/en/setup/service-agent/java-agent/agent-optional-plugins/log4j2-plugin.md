# log4j2 plugins

Here is an optional plugin `apm-log4j2-plugin` which can be used to replace the `apm-toolkit-log4j-2.x-activation` plugin. The difference between this two plugins is:

* `apm-toolkit-log4j-2.x-activation` only support log4j2's sync mode, when we enabled log4j2's async mode using `Log4jContextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector`, this plugin(%traceId) cannot print the traceid.
* `apm-log4j2-plugin` supports both sync and async mode of log4j2

## how to use

* 	Copy `agent/optional-plugins/apm-log4j2-plugin-x.jar` to agent/plugins, restarting the agent can enable the plugin.
* Config the `[%X{SW-TraceId}]` pattern in your `log4j2.xml`

```xml
<Appenders>
    <Console name="Console" target="SYSTEM_OUT">
        <PatternLayout pattern="%d [%X{SW-TraceId}] %-5p %c{1}:%L - %m%n"/>
    </Console>
</Appenders>
```

* 	When you use -javaagent to active the skywalking tracer, log4j2 will output traceId, if it existed. If the tracer is inactive, the output will be empty.




