# 编写 OAL(可观测性分析语言) 脚本
阅读 [OAL 概述](../../../en/concepts-and-designs/oal.md) 以了解oal脚本。

## 在源代码中查找oal脚本
官方official_analysis.oal在[这里](../../../../oap-server/generated-analysis/src/main/resources/official_analysis.oal).

## 生成工具
`oap-server/generate-tool` 模块包含编译工具的源代码。 该工具已经集成在
maven编译阶段。 所以，除非您想要更改工具源代码，否则无需进行任何设置。

运行 `./mvnw compile` 或 `./mvnw package`, 生成的oal脚本代码位于`oap-server/generate-tool/target/generated-sources/oal/*`.

## 编写并重新编译
您可以更改`official_analysis.oal`脚本, 然后重新编译代码.
生成的代码位于 **oap-server/generated-analysis/target/generated-sources/oal**.