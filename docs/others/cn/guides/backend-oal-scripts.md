# 官方 OAL(可观测性分析语言) 脚本
首先请阅读[OAL 介绍](../../../en/concepts-and-designs/oal.md).

发行版本中官方脚本:`generated-analysis-x.y.z.jar/official_analysis.oal`，并且源代码库在模块`server-core`中**src/main/resources/official_analysis.oal**。
**注意**, 这个文件虽然包含在发行版本中，但是在项目运行期间不会起任何作用。您需要使用OAL工具器去真实的分析代码。所生成的代码在`server-core`模块**org.apache.skywalking.oap.server.core.analysis.generated**中

此脚本中命名的所有度量标准都可用于警报和UI查询。 当然，你可以改变这个
脚本并重新生成分析过程和度量标准，例如添加过滤条件。

如果您尝试添加或删除某些指标，UI项目可能出现问题，我们建议您在计划时执行此操作是
基于自定义分析核心构建自己的UI。