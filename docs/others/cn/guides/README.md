# 贡献指南
你可以通过以下方式，为SkyWalking社区做成贡献。

- 审阅SkyWalking的文档，指出或修复文档不准确的地方，也可以将SkyWalking文档翻译成其他语言。
- 下载我们的[发布版本](http://skywalking.apache.org/downloads/)，尝试用其监控你的应用程序，并向我们反馈您的想法、疑问或使用案例。
- 阅读我们的源码，带着细节信息向我们提问。
- 发现bug时，可在这里[提交问题](https://github.com/apache/incubator-skywalking/issues), 并且您也可以尝试修复它。
- 你可以从完成[社区需要的帮助](https://github.com/apache/incubator-skywalking/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22)，
着手做起.
- 提交问题或讨论在[GitHub问题处](https://github.com/apache/incubator-skywalking/issues/new).
- 通过[网页邮件列表](https://lists.apache.org/list.html?dev@skywalking.apache.org)查看所有邮件的讨论，如果您是SkyWalking项目的committer，可在浏览器模式下登录并使用邮件列表。否则，根据按照下面进行订阅。
- 问题的报告和讨论也可在 `dev@skywalking.apache.org`进行， 
发送邮件到`dev-subscribe@skywalking.apache.org`，按照回复订阅邮件列表。


## 与我们联系
以下所有渠道均向社区开放，您可以选择自己喜欢的方式。

* 提交[问题](https://github.com/apache/incubator-skywalking/issues)
* 邮件列表: **dev@skywalking.apache.org**. 发送邮件到 `dev-subscribe@skywalking.apache.org`，按照回复订阅邮件列表。
* [Gitter](https://gitter.im/openskywalking/Lobby)
* QQ群: 392443393

## 对于代码开发者
作为开发者，首先阅读[编译指南](How-to-build.md). 它会告诉你如何在本地构建。

### 项目扩展
SkyWalking项目支持许多扩展现有功能的方法。 如果你对这些方式感兴趣，
阅读以下指南。

- [Java agent 插件开发指南](Java-Plugin-Development-Guide.md).
本指南将帮助您开发SkyWalking Java agent插件以支持更多框架或组件。 无论是进行开源插件或私有插件的开发都需要阅读这个指南。
- 如果您想开发其他语言的探针或组件，请阅读[组件库定义和扩展](Component-library-settings.md) 文档.
- [数据存储扩展开发指南](storage-extention.md). 除了官方已经支持的存储实现外，可帮助钱在的贡献者实现新的数据存储的实现。
- [通过oal脚本自定义分析](write-oal.md). 指导您使用oal脚本来提供自己所需的指标。
### UI开发者
Skywalking UI 由静态页面和Web容器构成。

- **静态页面** 是建立在 [Ant Design Pro](https://pro.ant.design/)之上，Skywalking UI源码托管在[Skywalking UI](https://github.com/apache/incubator-skywalking-ui)。
- **Web容器** 源代码在`apm-webapp`模块中。这是一个简单的zuul代理，用于托管静态资源并使用GraphQL想后端发送查询请求。

## 发布
[Apache发布指南](How-to-release.md)向提交者团队介绍了正式的Apache版本发布流程，以避免破坏任何Apache规则。如果您在重新分发中保留我们的许可和通知，则Apache许可允许每个人重新分发。