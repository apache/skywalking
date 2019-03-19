# 安装部署
根据您使用的探针类型来进行安装，如果你不明白什么是探针，请先阅读[概念和设计](https://github.com/apache/incubator-skywalking/tree/v6.0.0-GA/docs/others/cn/concepts-and-designs)。

**重要提示：请首先确认被监视服务器上的系统时间与OAP服务器上的时间相同。**

## 下载官方发行包
- Backend、UI和Java Agent都是由Apache官方提供: [官方包下载](http://skywalking.apache.org/downloads/)
- 通过下边的链接可以找到 .Net Agent的下载地址: [Getting started document](https://github.com/OpenSkywalking/skywalking-netcore#getting-started)
- 通过下边的链接可以找到Nodejs Agent的下载地址: [English document](https://github.com/OpenSkywalking/skywalking-nodejs/blob/master/docs/README.md#documents)
- 通过下边的链接可以下载PHP Agent: [English document](https://github.com/OpenSkywalking/skywalking-nodejs/blob/master/docs/README.md#documents)

## 服务中不同语言代理
- java agent: 如何在你开发的服务中，不需要修改任何代码来安装java agent
- .Net Core agent: 有关详细信息，请参阅DOTNET Core代理项目文档。
- Node.js agent: 有关更多详细信息，请参阅Node.js服务器端代理项目文档。
- PHP agent: 有关更多详细信息，请参阅PHP服务器端代理项目文档。

## 服务网格
- istio
[在istio中使用SkyWalking](https://github.com/apache/incubator-skywalking/blob/v6.0.0-GA/docs/en/setup/istio/README.md): 介绍如何使用Istio Mixer旁路适配器与SkyWalking配合使用。

## 安装后端服务Backend
按照后端和UI设置文档来了解和配置不同场景的后端，并打开高级功能。

## 变更日志
后端服务、UI和Java agent的所有变动都可以在[这里](https://github.com/apache/incubator-skywalking/blob/v6.0.0-GA/CHANGES.md)找到。

