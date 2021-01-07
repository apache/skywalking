## Support custom trace ignore
Here is an optional plugin `apm-trace-ignore-plugin`

**Notice:**
Sampling still works when the trace ignores plug-in activation.

## Introduce
- The purpose of this plugin is to filter endpoint which are expected to be ignored by the tracing system.
- You can setup multiple URL path patterns, The endpoints match these patterns wouldn't be traced.
- The current matching rules follow `Ant Path` match style , like `/path/*`, `/path/**`, `/path/?`.
- Copy `apm-trace-ignore-plugin-x.jar` to `agent/plugins`, restarting the `agent` can effect the plugin.                                                                                                         

## How to configure
There are two ways to configure ignore patterns. Settings through system env has higher priority.
 1. Set through the system environment variable,you need to add `skywalking.trace.ignore_path` to the system variables, the value is the path that you need to ignore, multiple paths should be separated by `,`
 2. Copy`/agent/optional-plugins/apm-trace-ignore-plugin/apm-trace-ignore-plugin.config` to `/agent/config/` dir, and add rules to filter traces
```
trace.ignore_path=/your/path/1/**,/your/path/2/**
```

