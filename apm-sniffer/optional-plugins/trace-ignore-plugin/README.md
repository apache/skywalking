## Support custom trace ignore
Here is an optional plugin `apm-trace-ignore-plugin`

## Introduce
- The role of this plugin is to filter personalized services that are tracked.
- You can set up a number of paths that need to be ignored, means the `agent` won't send the traces to `collector`.
- The current matching rule follows `Ant Path` match style , like `/path/*`, `/path/**`, `/path/?`.
- Copy `apm-trace-ignore-plugin-x.jar` to `agent/plugins`, restarting the `agent` can take effect.
- [Skywalking-使用可选插件 apm-trace-ignore-plugin](https://blog.csdn.net/u013095337/article/details/80452088) have a detailed introduction.
                                                                                                         

## How to configure
There are two ways of configuration. Any one can be used. The priority of the configuration is from high to low.
 1. Configuring in the system environment variable,you need to add `skywalking.trace.ignore_path` to the system variables, the value is the path that you need to ignore,many of them are separated by `,`
 2. Copy`/agent/optional-plugins/apm-trace-ignore-plugin/apm-trace-ignore-plugin.config` to `/agent/config/` dir,add config 
```
trace.ignore_path=/your/path/1/**,/your/path/2/**
```

