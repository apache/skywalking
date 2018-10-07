# SkyWalking 6 中文文档
注：中文文档由社区志愿者提供，官方文档以英文为准。
# 欢迎
**这是SkyWalking 6 中文文档。期待加入我们**

您可以从这里了解**SkyWalking**的架构、部署和使用，以及如何基于**SkyWalking**进行开发。

- [概念和设计](./concepts-and-designs/README.md)。概念和设计解释了SkyWalking最重要的核心理念。如果您想了解**SkyWalking**酷炫的特性以及可视化是如何运转的，您可以从这里学习。
- [安装部署](./setup/README.md)。 安装程序指南包含了在不同方案中安装SkyWalking的方式。同时，作为一个平台，它提供了包括监控和报警的几种提供可观察性的方法。
- [贡献指南](./guides/README.md)。指南适用于PPMC，提交者或新贡献者。您可以通过这里从一开始就知道如何参与贡献。
- [协议](./protocols/README.md)。协议说明了探针与后端之间的通讯方式。任何对上报链路探测数据感兴趣的人都应该阅读这个。
- [常见问题解答](./FAQ/README.md)。当您遇到问题时，请先查看此处，此处包括了清单、已知的安装问题和二次开发实验等。

此外，此处提供了一些有意思或者你会感兴趣的链接：

- [Apache SkyWalking发布页面](http://skywalking.apache.org/downloads/)上提供了最新版本和以往的版本。更改日志请参考[这里](../CHANGES.md)。
- 你可以在[社区资源目录](https://github.com/OpenSkywalking/Community)中找到关于Skywalking的会议、在线视频或者文章。如果你也有与此相关的内容，欢迎向我们提交Pull Request。
- 我们一直在寻求帮助来改进我们的文档和代码，因此如果您发现问题，请不要犹豫，[提出问题](https://github.com/apache/incubator-skywalking/issues/new)
或者更好的是，通过Pull Request提交您的贡献，来帮助改善。

对于其他语言的文档，由我们的社区提供，[官方文档](../../README.md)以英文文档为准。
- [中文 :cn:](./README.md)

___
### 使用 5.x 版本的用户
SkyWalking 5.x 仍受社区支持。对于用户计划从5.x升级到6.x，您应该知道关于有一些概念的定义的变更。

最重要的两个改变了的概念是：
1. Application（在5.x中）更改为**Service**（在6.x中），Application Instance也更改为**Service Instance**。
2. Service（在5.x中）更改为**Endpoint**（在6.x中）。


