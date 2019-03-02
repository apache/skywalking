# 安装部署
安装部署基于你将要使用哪种方式。如果你还不太理解，请先参考[概念和设计](../concepts-and-designs/README.md)。

**重要: 请确认受监控的服务器的系统时间和OAP服务的是按保持一致。**


## 下载官方发布版本
- 后端，UI和Java探针通过Apache发布，你可以在 [Apache SkyWalking 下载页面](http://skywalking.apache.org/downloads/)找到.

## 服务中的语言探针

- [Java探针](service-agent/java-agent/README.md). 介绍了在不改代码的情况下将Java探针安装在服务中。


下面的探针和SDK兼容SkyWalking的格式和协议，但是由第三方维护。
你可以去社区的项目中找到发行版和使用方式。

- [SkyAPM .NET Core 探针](https://github.com/SkyAPM/SkyAPM-dotnet). 请阅读.NET Core 探针文档了解详情。

- [SkyAPM Node.js 探针](https://github.com/SkyAPM/SkyAPM-nodejs). 请阅读Node.js server side 探针文档了解详情。

- [SkyAPM PHP SDK](https://github.com/SkyAPM/SkyAPM-php-sdk). 请阅读 PHP 探针文档了解详情。

## 在服务网格中部署
  - Istio
    - [SkyWalking on Istio](istio/README.md). 介绍如何避开Istio Mixer的适配器来运行Skywalking。
    

## 安装后端
参考[后端和前端部署文档](backend/backend-ui-setup.md)来学习和配置，或者开启新特性以在不同场景下使用。

## 更新日志
后端, 前端和Java探针的所有更新可[参考](../../../../CHANGES.md).

