## 工程编译指南
本文档用于指导开发者，在本地开发环境中编译工程。

### 前言
因为工程结构和代码依赖会随版本变化，如果读者熟悉travis-ci,则可直接参考[.travis.yml](https://github.com/wu-sheng/sky-walking/blob/master/.travis.yml)

### 编译步骤
1. 准备环境，jdk8，Maven
1. 执行`mvn clean package`

### 在IntelliJ IDEA中编译工程
上述步骤在命令行中，能够很好的编译工程，但导入到编译器中的工程依然会有一些报错，我们需要进行几步简单的操作。
1. 在IntelliJ Terminal中，执行`mvn compile -Dmaven.test.skip=true`进行编译
1. 设置gRPC的自动生成代码目录，为源码目录
  - **apm-network/target/generated--sources/protobuf**目录下的`grpc-java`和`java`目录
  - **apm-collector/apm-collector-remote/apm-remote-grpc-provider/target/protobuf**目录下的`grpc-java`和`java`目录

注：从3.2开始，网络通讯协议引入GRPC，所以增加上述的步骤
