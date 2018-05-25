# 追踪忽略增强
提供了一个可选插件 `apm-trace-ignore-plugin`

## 介绍
- 这个插件的作用是对追踪忽略的增强.
- 你可以设置多个需要被忽略的路径, 意味着包含这些路径的 `TraceContext` 不会被`agent`发送到 `collector`.
- 当前的路径匹配规则是 `Ant Path Match` , 例如 `/path/*`, `/path/**`, `/path/?`.
- 插件的目录里面提供了详细的使用说明, 你可以在 `/agent/optional-plugins/apm-trace-ignore-plugin`下找到`README.md`
- [Skywalking-使用可选插件 apm-trace-ignore-plugin](https://blog.csdn.net/u013095337/article/details/80452088) 有详细使用介绍