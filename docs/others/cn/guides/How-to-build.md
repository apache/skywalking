# 编译指南
本文档会介绍Skywalking在maven和您使用的IDE下如何进行编译和构建.

## 项目构建
**因为我们使用了`Git submodule`，所以我们建议不要使用`GitHub`标签或发布页面来下载用于编译的源代码。**

### 从GitHub代码构建
1. 准备git、JDK8和maven3。
1. `git clone https://github.com/apache/incubator-skywalking.git`
1. `cd incubator-skywalking/`
1. 使用`git checkout [tagname]`切换对应的标签代码版本(可选项，如果要从源代码构建版本，则切换）
1. `git submodule init`
1. `git submodule update`
1. 运行 `./mvnw clean package -DskipTests`
1. 打包后的代码在`/dist`文件夹中(Linux系统使用.tar.gz文件 and Windows使用.zip文件)。

### 从Apache源代码发行版本构建
- 什么是 `Apache源代码发行版本`?

对于每个正式的Apache发行版本，都有一个完整且独立的源代码tar包，其中包含所有源代码。您可以从[SkyWalking Apache 下载页面](http://skywalking.apache.org/downloads/)下载它。 编译时不需要与git相关的东西。 只需按照以下步骤操作

1. 准备JDK8和maven3
1. 运行 `./mvnw clean package -DskipTests`
1. 打包后的代码在`/dist`文件夹中(Linux系统使用.tar.gz文件 and Windows使用.zip文件)。

## 设置IntelliJ IDEA
1. 将项目按照maven项目的形式导入进来。
1. 运行 `./mvnw compile -Dmaven.test.skip=true`编译项目并生成源代码。 因为Skywalking使用gRPC和protobuf。
1. 所以还需要设置 **Generated Source Codes** 文件夹.
    * **apm-protocol/apm-network/target/generated-sources/protobuf** 文件夹下的`grpc-java` 和 `java`
    * **oap-server/server-core/target/generated-sources/protobuf** 文件夹下的`grpc-java` 和 `java`
    * **oap-server/server-receiver-plugin/skywalking-istio-telemetry-receiver-plugin/target/generated-sources/protobuf** 文件夹下的`grpc-java` 和 `java`
    * **oap-server/generate-tool/target/generated-sources** 文件夹下的 `antlr4`
    * **oap-server/generated-analysis/target/generated-sources** 文件夹下的 `oal`
    
## 设置Eclipse IDE
1. 将项目按照maven项目的形式导入进来。
2. 要支持多个源目录，需要添加以下配置 `incubator-skywalking/pom.xml`:

```
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>build-helper-maven-plugin</artifactId>
    <version>1.8</version>
    <executions>
        <execution>
            <id>add-source</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>add-source</goal>
            </goals>
            <configuration>
                <sources>
                    <source>src/java/main</source>
                    <source>apm-protocol/apm-network/target/generated-sources/protobuf</source>
                    <source>apm-collector/apm-collector-remote/collector-remote-grpc-provider/target/generated-sources/protobuf</source>
               </sources>
            </configuration>
        </execution>
    </executions>
</plugin>
```
3. 添加以下配置，让eclipse的M2e插件支持执行的解决方案配置

```
<pluginManagement>
    <plugins>
    <!--This plugin's configuration is used to store Eclipse m2e settings 
    only. It has no influence on the Maven build itself. -->
        <plugin>
            <groupId>org.eclipse.m2e</groupId>
            <artifactId>lifecycle-mapping</artifactId>
            <version>1.0.0</version>
            <configuration>
                <lifecycleMappingMetadata>
                    <pluginExecutions>
                        <pluginExecution>
                            <pluginExecutionFilter>
                                <groupId>org.codehaus.mojo</groupId>
                                <artifactId>build-helper-maven-plugin</artifactId>
                                <versionRange>[1.8,)</versionRange>
                                <goals>
                                    <goal>add-source</goal>
                                </goals>
                            </pluginExecutionFilter>
                        </pluginExecution>
                    </pluginExecutions>
                </lifecycleMappingMetadata>
            </configuration>
        </plugin>
    </plugins>
</pluginManagement>
```
4. 将Google guava依赖项添加到 **apm-collector-remote/collector-remote-grpc-provider/pom.xml**文件中

```
<dependency>
   <groupId>com.google.guava</groupId>
   <artifactId>guava</artifactId>
   <version>24.0-jre</version>
</dependency>
```
5. 运行 `./mvnw compile -Dmaven.test.skip=true`
6. 运行 `maven update`. update命令前不能有clean(这将清除complie生成的proto转换Java文件)
7. 运行 `./mvnw compile` 编译 collector-remote-grpc-provider 和 apm-protocol
8. 刷新项目
