# 什么是sky-walking应用程序工具包?
Sky-walking应用程序工具包是一系列的类库，由skywalking团队提供。通过这些类库，你可以在你的应用程序内，访问sky-walking的一些内部信息.

_**最为重要的是**_, 即使你移除skywalking的探针，或者不激活探针，这些类库也不会对应用程序有任何影响，也不会影响性能.

# 工具包提供以下核心能力
1. 将追踪信息和log组件集成，如log4j, log4j2 和 logback
1. 兼容CNCF OpenTracing标准的手动埋点
1. 使用Skywalking专有的标注和交互性API

_**注意**: 所有的应用程序工具包都托管在bitray.com/jcenter. 同时请确保你使用的开发工具包和skywalking的agent探针版本一致._