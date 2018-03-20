## 工程编译指南
本文档用于指导开发者，在本地开发环境中编译工程。

### 前言
因为工程结构和代码依赖会随版本变化，如果读者熟悉travis-ci,则可直接参考[.travis.yml](../../.travis.yml)

**工程使用Git Submodule，所以不建议使用GitHub的tag和release页面的源码下载，来编译工程**

### 从GitHub下载代码编译
1. 准备环境: git, jdk8，Maven
1. `git clone https://github.com/apache/incubator-skywalking.git`
1. `cd incubator-skywalking/`
1. 切换到tag `git checkout [tagname]` (可选，当需要编译发行版本时，请是切换到指定分支)
1. `git submodule init`
1. `git submodule update`
1. 执行`mvn clean package  -DskipTests`
1. 生成包在`/dist`目录下（.tar.gz是linux环境，.zip是windows环境）

### 从Apache源码镜像下载编译
1. 准备环境: jdk8，Maven
1. 执行`mvn clean package -DskipTests`
1. 生成包在`/dist`目录下（.tar.gz是linux环境，.zip是windows环境）


## 在IntelliJ IDEA中编译工程
上述步骤在命令行中，能够很好的编译工程，但导入到编译器中的工程依然会有一些报错，我们需要进行几步简单的操作。
1. 在IntelliJ Terminal中，执行`mvn compile -Dmaven.test.skip=true`进行编译
1. 设置gRPC的自动生成代码目录，为源码目录
  - **apm-protocol/apm-network/target/generated-sources/protobuf**目录下的`grpc-java`和`java`目录
  - **apm-collector/apm-collector-remote/apm-remote-grpc-provider/target/protobuf**目录下的`grpc-java`和`java`目录

## 编译Resin-3， Resin-4 和 Oracle JDBC 驱动插件
为了遵守Apache关于协议（License）的相关要求，不符合Apache相关要求的类库所对应的Plugin不会自动编译。如需编译对应的插件，
需要手动下载驱动或类库，并将文件拷贝到`ci-dependencies/`中，运行`mvn package`进行编译。

`ci-dependencies/`下对应的类库文件名为：
* resin-3.0.9.jar
* resin-4.0.41.jar
* ojdbc14-10.2.0.4.0.jar
