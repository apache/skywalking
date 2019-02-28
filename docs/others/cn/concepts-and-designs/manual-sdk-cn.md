## 手动埋点SDK
我们目前并没有提供任何关于手动埋点的SDK。

欢迎考虑贡献以下语言的探针：
- Go
- Python
- C++

## 什么是SkyWalking的格式和传输协议？
可以在[协议文档](../protocols/README.md)中查看细节.

## SkyWalking可以对以上的语言提供OpenCensus的出口？
在我写这份文件的时候是不支持的。因为OC(OpenCensus)并不提供上下文可扩展机制的支持，也没有提供在操纵span的时候提供任何的hook机制。
SkyWalking依靠这些去传播更多的东西而不是trace id和span id。

我们已经在讨论中了，你可以查看https://github.com/census-instrumentation/opencensus-specs/issues/70。
在OC正式提供这一点后, 我们才可以提供。

## 那Zipkin埋点SDK可以吗？
可以在后端文档中查看[Zipkin接收器](../setup/backend/backend-receivers.md)，它与上面提到的有些不同。
