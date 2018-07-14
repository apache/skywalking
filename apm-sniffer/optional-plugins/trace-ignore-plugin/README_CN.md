## 个性化服务过滤
提供了一个可选插件 `apm-trace-ignore-plugin`

## 介绍
- 这个插件的作用是对追踪的个性化服务过滤.
- 你可以设置多个需要忽略的URL路径, 意味着包含这些路径的`追踪信息`不会被`agent`发送到 `collector`.
- 当前的路径匹配规则是 `Ant Path`匹配风格 , 例如 `/path/*`, `/path/**`, `/path/?`.
- 将`apm-trace-ignore-plugin-x.jar`拷贝到`agent/plugins`后，重启探针即可生效
- [Skywalking-使用可选插件 apm-trace-ignore-plugin](https://blog.csdn.net/u013095337/article/details/80452088) 有详细使用介绍

## 如何配置路径 
有两种配置方式，可使用任意一种，配置生效的优先级从高到低
 1. 在系统环境变量中配置，你需要在系统变量中添加`skywalking.trace.ignore_path`, 值是你需要忽略的路径，多个以`,`号分隔
 2. 将`/agent/optional-plugins/apm-trace-ignore-plugin/apm-trace-ignore-plugin.config` 复制或剪切到 `/agent/config/` 目录下，加上配置
```
trace.ignore_path=/your/path/1/**,/your/path/2/**
```

